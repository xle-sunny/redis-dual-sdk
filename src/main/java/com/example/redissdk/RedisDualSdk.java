package com.example.redissdk;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.core.DbapiRedisClient;
import com.example.redissdk.core.JedisRedisClient;
import com.example.redissdk.core.RedisSdkTemplate;
import com.example.redissdk.nacos.NacosConfigManager;
import com.example.redissdk.nacos.NacosOptions;
import com.example.redissdk.route.RouteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * SDK 入口（纯 Java，无 Spring 依赖）。业务程序提供 Nacos 连接信息完成初始化：
 *
 * <pre>
 * RedisDualSdk sdk = RedisDualSdk.builder()
 *         .nacosServerAddr("nacos.internal:8848")
 *         .nacosNamespace("prod")                 // 可选
 *         .nacosAuth("nacos", "nacos")            // 可选
 *         .dataId("redis-sdk.yaml")               // 默认 redis-sdk.yaml
 *         .group("DEFAULT_GROUP")                 // 默认 DEFAULT_GROUP
 *         .build();
 *
 * RedisSdkTemplate redis = sdk.template();
 * redis.set("user:1001:name", "tom");
 * String name = redis.get("user:1001:name");
 *
 * // 应用退出时释放资源
 * sdk.close();
 * </pre>
 *
 * 初始化流程：拉取 Nacos 配置 -> 构建 Jedis 通道（单机/集群） + DBAPI Feign 通道 ->
 * 注册配置监听（路由/灰度变更即时生效；连接配置变更自动重建对应通道，业务无感）。
 */
public class RedisDualSdk implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisDualSdk.class);

    private final NacosConfigManager configManager;
    private final JedisRedisClient jedisClient;
    private final DbapiRedisClient dbapiClient;
    private final RedisSdkTemplate template;

    private RedisDualSdk(NacosOptions options) {
        this.configManager = new NacosConfigManager(options);
        RedisSdkProperties initial = configManager.current();
        this.jedisClient = new JedisRedisClient(initial);
        this.dbapiClient = new DbapiRedisClient(initial, configManager);
        RouteStrategy routeStrategy = new RouteStrategy(configManager::current);
        this.template = new RedisSdkTemplate(jedisClient, dbapiClient, routeStrategy);
        registerRefreshListener(initial);
    }

    /** 配置变更时按指纹判断是否需要重建连接：路由/灰度变化无需重建，连接信息变化才重建 */
    private void registerRefreshListener(RedisSdkProperties initial) {
        final String[] connFp = {initial.connectionFingerprint()};
        final String[] dbapiFp = {initial.dbapiFingerprint()};
        configManager.addListener(newProps -> {
            String newConnFp = newProps.connectionFingerprint();
            if (!newConnFp.equals(connFp[0])) {
                connFp[0] = newConnFp;
                jedisClient.refresh(newProps);
                log.info("redis connection config changed, jedis channel rebuilt");
            }
            String newDbapiFp = newProps.dbapiFingerprint();
            if (!newDbapiFp.equals(dbapiFp[0])) {
                dbapiFp[0] = newDbapiFp;
                dbapiClient.refresh(newProps);
                log.info("dbapi config changed, feign channel rebuilt");
            }
        });
    }

    /** 业务读写 Redis 的唯一门面 */
    public RedisSdkTemplate template() {
        return template;
    }

    /** 当前生效配置（只读用途） */
    public RedisSdkProperties currentProperties() {
        return configManager.current();
    }

    @Override
    public void close() {
        try {
            jedisClient.close();
        } catch (Exception e) {
            log.warn("close jedis client failed", e);
        }
        configManager.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final NacosOptions options = new NacosOptions();

        /** Nacos 地址，如 127.0.0.1:8848 */
        public Builder nacosServerAddr(String serverAddr) {
            options.setServerAddr(serverAddr);
            return this;
        }

        public Builder nacosNamespace(String namespace) {
            options.setNamespace(namespace);
            return this;
        }

        public Builder nacosAuth(String username, String password) {
            options.setUsername(username);
            options.setPassword(password);
            return this;
        }

        /** 业务方也可直接提供完整的 nacos Properties（serverAddr/namespace/username/...） */
        public Builder nacosProperties(Properties properties) {
            options.setRawProperties(properties);
            return this;
        }

        /** SDK 配置所在 dataId（YAML），默认 redis-sdk.yaml */
        public Builder dataId(String dataId) {
            options.setDataId(dataId);
            return this;
        }

        public Builder group(String group) {
            options.setGroup(group);
            return this;
        }

        public Builder configTimeoutMs(long timeoutMs) {
            options.setConfigTimeoutMs(timeoutMs);
            return this;
        }

        public RedisDualSdk build() {
            return new RedisDualSdk(options);
        }
    }
}
