package com.example.redissdk.core;

import com.example.redissdk.route.RouteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务方唯一入口（Facade）。API 与常见 Redis 客户端一致，业务代码无感：
 * 底层按配置在 Jedis 通道与 DBAPI 通道间路由，支持灰度切换与读补偿。
 *
 * 路由规则（由 Nacos 动态配置驱动，见 RedisSdkProperties.Route）：
 * - 写：writeViaDbapi(key) 为 true 走 DBAPI，否则走 Jedis；目标态为写全量走 DBAPI；
 * - 读：readDirectDbapi(key) 为 true 直接走 DBAPI；否则先读 Jedis，
 *       若数据不全（null / 集合缺元素 / hash 缺字段）且处于 JEDIS_FIRST 模式，则回源 DBAPI 取全量数据；
 * - Jedis 通道异常时（网络/连接问题），读操作自动降级到 DBAPI，保证可用性。
 */
public class RedisSdkTemplate {

    private static final Logger log = LoggerFactory.getLogger(RedisSdkTemplate.class);

    private final RedisClient jedisClient;
    private final RedisClient dbapiClient;
    private final RouteStrategy routeStrategy;

    public RedisSdkTemplate(RedisClient jedisClient, RedisClient dbapiClient, RouteStrategy routeStrategy) {
        this.jedisClient = jedisClient;
        this.dbapiClient = dbapiClient;
        this.routeStrategy = routeStrategy;
    }

    // ================= 写操作 =================

    public String set(String key, String value) {
        return writeClient(key).set(key, value);
    }

    public String setex(String key, long seconds, String value) {
        return writeClient(key).setex(key, seconds, value);
    }

    public String mset(String... keysValues) {
        return writeClient(firstKey(keysValues)).mset(keysValues);
    }

    public long incrBy(String key, long increment) {
        return writeClient(key).incrBy(key, increment);
    }

    public long hset(String key, String field, String value) {
        return writeClient(key).hset(key, field, value);
    }

    public long hset(String key, Map<String, String> hash) {
        return writeClient(key).hset(key, hash);
    }

    public long hsetnx(String key, String field, String value) {
        return writeClient(key).hsetnx(key, field, value);
    }

    public long hdel(String key, String... fields) {
        return writeClient(key).hdel(key, fields);
    }

    public long lpush(String key, String... values) {
        return writeClient(key).lpush(key, values);
    }

    public long rpush(String key, String... values) {
        return writeClient(key).rpush(key, values);
    }

    public String lpop(String key) {
        return writeClient(key).lpop(key);
    }

    public String rpop(String key) {
        return writeClient(key).rpop(key);
    }

    public long sadd(String key, String... members) {
        return writeClient(key).sadd(key, members);
    }

    public long srem(String key, String... members) {
        return writeClient(key).srem(key, members);
    }

    public long zadd(String key, double score, String member) {
        return writeClient(key).zadd(key, score, member);
    }

    public long del(String... keys) {
        return writeClient(firstKey(keys)).del(keys);
    }

    public long expire(String key, long seconds) {
        return writeClient(key).expire(key, seconds);
    }

    public long persist(String key) {
        return writeClient(key).persist(key);
    }

    // ================= 读操作（Jedis 优先 + 数据不全回源 DBAPI） =================

    public String get(String key) {
        return read(key, c -> c.get(key), v -> v != null);
    }

    /**
     * 批量读：Jedis 结果中存在 null（数据不全）时回源 DBAPI 获取全量数据。
     */
    public List<String> mget(String... keys) {
        return read(firstKey(keys), c -> c.mget(keys),
                v -> v != null && v.size() == keys.length && !v.contains(null));
    }

    public String hget(String key, String field) {
        return read(key, c -> c.hget(key, field), v -> v != null);
    }

    /**
     * 读取整个 hash：Jedis 结果为空视为数据不全，回源 DBAPI。
     */
    public Map<String, String> hgetAll(String key) {
        return read(key, c -> c.hgetAll(key), v -> v != null && !v.isEmpty());
    }

    /**
     * 读取整个 hash，并校验必须包含 expectedFields 中的全部字段，
     * 缺任一字段视为数据不全，回源 DBAPI 获取全量数据。
     */
    public Map<String, String> hgetAll(String key, Set<String> expectedFields) {
        return read(key, c -> c.hgetAll(key),
                v -> v != null && !v.isEmpty()
                        && (expectedFields == null || v.keySet().containsAll(expectedFields)));
    }

    public List<String> hmget(String key, String... fields) {
        return read(key, c -> c.hmget(key, fields),
                v -> v != null && v.size() == fields.length && !v.contains(null));
    }

    public Set<String> hkeys(String key) {
        return read(key, c -> c.hkeys(key), v -> v != null && !v.isEmpty());
    }

    public List<String> hvals(String key) {
        return read(key, c -> c.hvals(key), v -> v != null && !v.isEmpty());
    }

    public boolean hexists(String key, String field) {
        return read(key, c -> c.hexists(key, field), v -> v != null && v);
    }

    public long hlen(String key) {
        return read(key, c -> c.hlen(key), v -> v != null && v >= 0);
    }

    public long hstrlen(String key, String field) {
        return read(key, c -> c.hstrlen(key, field), v -> v != null && v >= 0);
    }

    public List<String> hrandfield(String key, int count) {
        return read(key, c -> c.hrandfield(key, count), v -> v != null && !v.isEmpty());
    }

    public Map<String, String> hscan(String key, String cursor, String match, int count) {
        return read(key, c -> c.hscan(key, cursor, match, count), v -> v != null && !v.isEmpty());
    }

    public List<String> lrange(String key, long start, long stop) {
        return read(key, c -> c.lrange(key, start, stop), v -> v != null && !v.isEmpty());
    }

    public Set<String> smembers(String key) {
        return read(key, c -> c.smembers(key), v -> v != null && !v.isEmpty());
    }

    public List<String> zrangeByScore(String key, double min, double max) {
        return read(key, c -> c.zrangeByScore(key, min, max), v -> v != null && !v.isEmpty());
    }

    public boolean exists(String key) {
        return read(key, c -> c.exists(key), v -> v != null && v);
    }

    public long ttl(String key) {
        return read(key, c -> c.ttl(key), v -> v != null && v >= 0);
    }

    public List<String> scan(String cursor, String match, int count) {
        // scan操作使用cursor作为路由key，因为scan不基于具体key
        return read(cursor, c -> c.scan(cursor, match, count), v -> v != null && !v.isEmpty());
    }

    // ================= 内部路由 =================

    @FunctionalInterface
    public interface ReadOp<T> {
        T apply(RedisClient client);
    }

    @FunctionalInterface
    public interface Completeness<T> {
        boolean isComplete(T value);
    }

    private RedisClient writeClient(String key) {
        return routeStrategy.writeViaDbapi(key) ? dbapiClient : jedisClient;
    }

    /**
     * 读路由核心：
     * 1. 命中读灰度/前缀/DBAPI_ONLY -> 直接 DBAPI；
     * 2. 否则先读 Jedis；
     * 3. JEDIS_FIRST 模式下，Jedis 结果"不完整"或 Jedis 异常 -> 回源 DBAPI 取全量。
     */
    private <T> T read(String key, ReadOp<T> op, Completeness<T> completeness) {
        if (routeStrategy.readDirectDbapi(key)) {
            return op.apply(dbapiClient);
        }
        T value;
        try {
            value = op.apply(jedisClient);
        } catch (Exception e) {
            if (routeStrategy.readFallbackEnabled()) {
                log.warn("jedis read failed, fallback to DBAPI, key={}", key, e);
                return op.apply(dbapiClient);
            }
            throw e;
        }
        if (!completeness.isComplete(value) && routeStrategy.readFallbackEnabled()) {
            if (log.isDebugEnabled()) {
                log.debug("jedis read incomplete, fallback to DBAPI, key={}", key);
            }
            return op.apply(dbapiClient);
        }
        return value;
    }

    private static String firstKey(String[] keys) {
        return keys != null && keys.length > 0 ? keys[0] : null;
    }
}
