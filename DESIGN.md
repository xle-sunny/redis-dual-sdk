# Redis 0 分区双通道读写 SDK — 方案与详细设计

## 1. 背景与目标

业务程序需要读写 Redis 0 分区。为了将 Redis 访问逐步收敛到 DBAPI 服务（统一管控、审计、限流），需要一个对业务**无感**的 Java SDK：

- 纯 Java 实现，**不依赖 Spring 框架**；Nacos 连接信息由使用 SDK 的业务程序在初始化时传入；
- 双通道：① Jedis 客户端直连 Redis；② Feign（原生 feign-core）调用 DBAPI 服务代理读写；
- 通道切换由配置驱动，支持**灰度**（按流量百分比 / key 前缀逐步切换）；
- 目标态：**写操作全部走 DBAPI**；**读操作 Jedis 优先，数据不全时回源 DBAPI 取全量**；
- Redis 连接信息与 Feign(DBAPI) 连接信息统一从 **Nacos 配置中心**获取，支持动态刷新；
- 兼容 Redis 0 分区的两种架构：**集群模式（Cluster）与单机模式（Standalone）**。

## 2. 总体方案

```
业务代码
   │  初始化：RedisDualSdk.builder().nacosServerAddr(...).dataId(...).build()
   │  读写：只依赖统一门面 RedisSdkTemplate（API 与常见 Redis 客户端一致，业务无感）
   ▼
RedisDualSdk（SDK 入口，生命周期管理）
   │
   ├── NacosConfigManager（nacos-client：拉取配置 + 监听变更 + 服务发现）
   ▼
RedisSdkTemplate（门面 + 读补偿）
   │
   ├── RouteStrategy（路由/灰度决策，配置来自 Nacos，动态刷新）
   │
   ├── JedisRedisClient ──── UnifiedJedis ──┬── JedisPooled（单机模式）
   │        （直连通道）                     └── JedisCluster（集群模式）
   │
   └── DbapiRedisClient ─── DbapiFeignClient(原生 OpenFeign)
            （代理通道）        │ 按服务名从 Nacos 注册中心逐次选择健康实例（或直连 URL）
                               ▼
                          DBAPI 服务 ──► Redis 0 分区
```

关键点：

1. **业务无感**：业务只使用 `RedisSdkTemplate`，方法签名与 Jedis 风格一致（get/set/hget/hgetAll/…）。通道选择、灰度、回源逻辑全部在 SDK 内部完成。
2. **统一抽象**：`RedisClient` 接口定义全部支持的命令，`JedisRedisClient` 与 `DbapiRedisClient` 分别实现，门面按路由结果选择实现。
3. **集群/单机兼容**：Jedis 4.x 的 `UnifiedJedis` 是 `JedisPooled`（单机）与 `JedisCluster`（集群）的公共父类，按 `redis.sdk.mode` 构建对应实例，上层代码零差异。DBAPI 通道由 DBAPI 服务内部兼容，SDK 不感知。
4. **配置中心**：所有配置放在 Nacos（dataId 如 `redis-sdk.yaml`），SDK 内置 `NacosConfigManager`（基于原生 nacos-client）负责拉取、YAML 解析与监听热更新；**Nacos 连接参数（serverAddr/namespace/认证/dataId/group）由业务程序初始化时传入**。

## 3. 路由与灰度设计

### 3.1 写路由

| write-channel | 行为 |
|---|---|
| `JEDIS` | 全部走 Jedis（初始态） |
| `GRAY`  | 按 `CRC32(key) % 100 < write-gray-percent` 灰度到 DBAPI |
| `DBAPI` | 全部走 DBAPI（目标态） |

- 采用 **key 确定性哈希** 而非随机数：同一个 key 在灰度期间路由稳定，避免同 key 双通道交叉写导致的顺序问题。
- `gray-key-prefixes`：命中前缀的 key 强制走 DBAPI，支持按业务维度先行灰度。

灰度推进路径：`JEDIS` → `GRAY(10%→50%→100%)` → `DBAPI`，全程只改 Nacos 配置，无需发版。

### 3.2 读路由与读补偿

| read-mode | 行为 |
|---|---|
| `JEDIS_ONLY` | 只读 Jedis |
| `JEDIS_FIRST`（目标态） | 先读 Jedis；**数据不全**或 Jedis 异常时回源 DBAPI 取全量；可叠加 `read-gray-percent` 将部分 key 直接切到 DBAPI |
| `DBAPI_ONLY` | 只读 DBAPI |

**“数据不全”判定**（`RedisSdkTemplate` 内置，逐命令定义）：

| 命令 | 不全判定 |
|---|---|
| `get` / `hget` | 结果为 null |
| `mget` / `hmget` | 结果条数不足或含 null |
| `hgetAll` | 结果为空；重载 `hgetAll(key, expectedFields)` 支持业务声明必备字段，缺字段即回源 |
| `lrange` / `smembers` / `zrangeByScore` | 结果为空集合 |
| `exists` / `ttl` | key 不存在 |

回源后以 DBAPI 结果为准返回业务，保证读到全量数据。

### 3.3 容灾

- `JEDIS_FIRST` 模式下 Jedis 通道抛异常（连接/超时）自动降级 DBAPI，读高可用；
- DBAPI 通道异常统一抛 `RedisSdkException`，携带命令与 DBAPI 返回码，便于告警定位。

## 4. 配置设计（Nacos）

Nacos 连接信息由业务程序提供（见 §7 接入方式）；SDK 业务配置存于 dataId：`redis-sdk.yaml`（group DEFAULT_GROUP，均可自定义），完整示例见 `RedisSdkProperties` 类注释。核心结构：

```yaml
redis:
  sdk:
    enabled: true
    mode: cluster                # cluster / standalone —— 兼容两种架构
    standalone: { host: 127.0.0.1, port: 6379 }
    cluster:
      nodes: ["10.0.0.1:7000", "10.0.0.2:7000", "10.0.0.3:7000"]
    password: ${REDIS_PASSWORD}
    database: 0                  # 0 分区（单机模式生效；集群模式固定 db0）
    pool: { max-total: 64, max-idle: 16, min-idle: 4, max-wait-ms: 2000 }
    route:
      write-channel: GRAY        # JEDIS / GRAY / DBAPI
      write-gray-percent: 30     # 灰度期间逐步调大到 100
      read-mode: JEDIS_FIRST     # 目标态：Jedis 优先 + DBAPI 补偿
      read-gray-percent: 0
      gray-key-prefixes: ["order:"]
    dbapi:
      service-name: dbapi-service  # Nacos 注册中心服务名（负载均衡）
      url:                         # 或直连 URL，优先级更高
      partition: 0
      connect-timeout-ms: 1000
      read-timeout-ms: 2000
```

动态刷新机制（`NacosConfigManager` 监听配置变更）：

- **路由/灰度配置**：Nacos 变更 → 解析后整体替换当前配置实例，`RouteStrategy` 通过 Supplier 读取最新配置，**秒级生效，无需重启**；
- **连接配置**：按“配置指纹”对比（connectionFingerprint / dbapiFingerprint），仅当 Redis 连接或 DBAPI 连接相关配置变化时才重建对应通道（先建新后关旧，业务无感）；灰度百分比调整不会触发重建。
- 配置非法时保持当前配置不变并告警日志，避免错误配置击穿线上。

## 5. DBAPI 交互协议

Feign（原生 OpenFeign）接口：`POST /api/redis/execute`；寻址方式：配置 `dbapi.url` 则直连，否则按 `dbapi.service-name` 每次请求从 Nacos 注册中心选择健康实例（客户端负载均衡）。

```json
// 请求
{ "partition": 0, "command": "HGETALL", "args": ["user:1001"] }
// 响应
{ "code": 0, "msg": "ok", "data": { "name": "tom", "age": "18" } }
```

采用**通用命令模式**（command + args），DBAPI 侧一次性支持全部命令，后续 SDK 扩展命令无需 DBAPI 改动。`data` 按命令类型为字符串/数组/对象/数字，`DbapiRedisClient` 负责反序列化。若贵司 DBAPI 已有既定协议，只需替换 `DbapiFeignClient` 与 `DbapiRedisClient` 的 call/解析逻辑，其余模块不动。

## 6. 代码结构

```
com.example.redissdk
├── RedisDualSdk                    # SDK 入口（Builder 初始化，业务传入 Nacos 信息，生命周期管理）
├── config
│   └── RedisSdkProperties          # 全量配置项（Nacos 下发，动态刷新）+ 连接指纹
├── nacos
│   ├── NacosOptions                # 业务提供的 Nacos 连接参数
│   ├── NacosConfigManager          # 配置拉取/监听 + 服务发现（nacos-client）
│   └── RedisSdkConfigParser        # YAML -> RedisSdkProperties（兼容 kebab/camel）
├── core
│   ├── RedisClient                 # 统一命令抽象（String/Hash/List/Set/ZSet/Key）
│   ├── JedisRedisClient            # Jedis 直连通道（UnifiedJedis，单机/集群兼容）
│   ├── DbapiRedisClient            # DBAPI 代理通道（原生 Feign）
│   └── RedisSdkTemplate            # 业务唯一门面：写路由 + 读补偿
├── route
│   └── RouteStrategy               # 灰度/路由决策（CRC32 哈希 + 前缀白名单）
├── feign
│   ├── DbapiFeignClient            # Feign 接口（@RequestLine）
│   ├── DbapiClientFactory          # 构建 Feign 客户端（直连 URL / Nacos 服务发现 Target）
│   └── dto: DbapiRequest / DbapiResponse
└── exception
    └── RedisSdkException
```

## 7. 业务接入方式

1. 引入依赖：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>redis-dual-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

2. 初始化（Nacos 连接信息由业务程序提供，全局初始化一次，应用退出时 close）：

```java
RedisDualSdk sdk = RedisDualSdk.builder()
        .nacosServerAddr("nacos.internal:8848")
        .nacosNamespace("prod")            // 可选
        .nacosAuth("nacos", "nacos")       // 可选
        .dataId("redis-sdk.yaml")          // 默认 redis-sdk.yaml
        .group("DEFAULT_GROUP")            // 默认 DEFAULT_GROUP
        .build();                          // 也可 .nacosProperties(props) 直接传完整 Properties

RedisSdkTemplate redis = sdk.template();
```

3. 读写使用（与普通 Redis 客户端一致，业务无感）：

```java
redis.set("user:1001:name", "tom");          // 按配置路由 Jedis/DBAPI
Map<String,String> u = redis.hgetAll("user:1001");                 // Jedis 优先，空则回源 DBAPI
Map<String,String> u2 = redis.hgetAll("user:1001",
        new HashSet<>(Arrays.asList("name","age")));               // 缺字段即回源取全量
```

## 8. 上线与灰度切换步骤

1. 初始：`write-channel: JEDIS`，`read-mode: JEDIS_ONLY` —— 与存量行为完全一致；
2. 写灰度：`write-channel: GRAY`，`write-gray-percent` 10 → 30 → 50 → 100，观察 DBAPI QPS/错误率；
3. 写收口：`write-channel: DBAPI` —— 写全量走 DBAPI（目标态）；
4. 读切换：`read-mode: JEDIS_FIRST` —— 读 Jedis 优先，数据不全自动回源 DBAPI（目标态）；
5. 全程仅修改 Nacos 配置（SDK 监听变更实时生效），可随时回退上一步，业务无需发版重启。

## 9. 兼容性与限制

- 纯 Java，无 Spring 依赖；JDK 8+；依赖：Jedis 4.4.x（`UnifiedJedis` 统一单机/集群）、feign-core/feign-jackson 12.x、nacos-client 2.2.x、snakeyaml、jackson；
- 多 key 命令（mget/mset/del）按首 key 做路由决策；集群模式下多 key 命令需业务保证 key 落在同一 slot（hash tag），与直接使用 JedisCluster 的约束一致；
- 灰度期间同一 key 路由确定（CRC32），不存在通道抖动；但 Jedis 与 DBAPI 双写不同 key 集合期间，请保证 DBAPI 与直连写的是同一 Redis（本方案 DBAPI 即代理同一 0 分区，天然满足）。
