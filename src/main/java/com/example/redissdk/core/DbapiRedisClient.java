package com.example.redissdk.core;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.exception.RedisSdkException;
import com.example.redissdk.feign.DbapiClientFactory;
import com.example.redissdk.feign.DbapiFeignClient;
import com.example.redissdk.feign.dto.DbapiRequest;
import com.example.redissdk.feign.dto.DbapiResponse;
import com.example.redissdk.nacos.NacosConfigManager;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * DBAPI 通道：所有 Redis 命令转换为具体请求，经 Feign 调用 DBAPI 服务执行。
 * DBAPI 服务内部持有 Redis 连接（同样兼容集群/单机），SDK 侧不感知其架构。
 * Nacos 配置变更时可通过 {@link #refresh(RedisSdkProperties)} 重建 Feign 客户端。
 * 
 * 现在适配具体DBAPI接口规范，每个Redis命令对应独立的RESTful端点
 */
public class DbapiRedisClient implements RedisClient {

    private final NacosConfigManager nacos;
    private volatile DbapiFeignClient feignClient;
    private volatile int partition;

    public DbapiRedisClient(RedisSdkProperties properties, NacosConfigManager nacos) {
        this.nacos = nacos;
        this.feignClient = DbapiClientFactory.create(properties, nacos);
        this.partition = properties.getDbapi().getPartition();
    }

    /** Nacos 配置变更后重建 Feign 客户端（业务无感） */
    public synchronized void refresh(RedisSdkProperties properties) {
        this.feignClient = DbapiClientFactory.create(properties, nacos);
        this.partition = properties.getDbapi().getPartition();
    }

    private JsonNode executeRequest(DbapiRequest request, String operation) {
        DbapiResponse response;
        try {
            switch (operation) {
                case "hset":
                    response = feignClient.hset(request);
                    break;
                case "hmset":
                    response = feignClient.hmset(request);
                    break;
                case "hsetnx":
                    response = feignClient.hsetnx(request);
                    break;
                case "del":
                    response = feignClient.del(request);
                    break;
                case "hdel":
                    response = feignClient.hdel(request);
                    break;
                case "hget":
                    response = feignClient.hget(request);
                    break;
                case "hmget":
                    response = feignClient.hmget(request);
                    break;
                case "hgetall":
                    response = feignClient.hgetAll(request);
                    break;
                case "hkeys":
                    response = feignClient.hkeys(request);
                    break;
                case "hvals":
                    response = feignClient.hvals(request);
                    break;
                case "exists":
                    response = feignClient.exists(request);
                    break;
                case "hexists":
                    response = feignClient.hexists(request);
                    break;
                case "hlen":
                    response = feignClient.hlen(request);
                    break;
                case "hstrlen":
                    response = feignClient.hstrlen(request);
                    break;
                case "hrandfield":
                    response = feignClient.hrandfield(request);
                    break;
                case "scan":
                    response = feignClient.scan(request);
                    break;
                case "hscan":
                    response = feignClient.hscan(request);
                    break;
                case "expire":
                    response = feignClient.expire(request);
                    break;
                case "ttl":
                    response = feignClient.ttl(request);
                    break;
                case "persist":
                    response = feignClient.persist(request);
                    break;
                default:
                    throw new RedisSdkException("Unsupported operation: " + operation);
            }
        } catch (Exception e) {
            throw new RedisSdkException("DBAPI call failed, operation=" + operation, e);
        }
        if (response == null || !response.isSuccess()) {
            throw new RedisSdkException("DBAPI returned error, operation=" + operation
                    + ", code=" + (response == null ? "null" : response.getCode())
                    + ", message=" + (response == null ? "null" : response.getMessage()));
        }
        return response.getData();
    }

    private static String asString(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static long asLong(JsonNode node) {
        return node == null || node.isNull() ? 0L : node.asLong();
    }

    private static boolean asBoolean(JsonNode node) {
        return node != null && !node.isNull() && node.asBoolean();
    }

    private static List<String> asStringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            result.add(item == null || item.isNull() ? null : item.asText());
        }
        return result;
    }

    private static Map<String, String> asStringMap(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(e ->
                    result.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText()));
        }
        return result;
    }

    // ---------- String ----------
    @Override
    public String get(String key) {
        // DBAPI接口中没有GET，暂时使用HGET模拟或者需要添加GET接口
        throw new RedisSdkException("GET operation not supported by current DBAPI interface");
    }

    @Override
    public String set(String key, String value) {
        // DBAPI接口中没有SET，暂时使用HSET模拟或者需要添加SET接口
        throw new RedisSdkException("SET operation not supported by current DBAPI interface");
    }

    @Override
    public String setex(String key, long seconds, String value) {
        throw new RedisSdkException("SETEX operation not supported by current DBAPI interface");
    }

    @Override
    public List<String> mget(String... keys) {
        throw new RedisSdkException("MGET operation not supported by current DBAPI interface");
    }

    @Override
    public String mset(String... keysValues) {
        throw new RedisSdkException("MSET operation not supported by current DBAPI interface");
    }

    @Override
    public long incrBy(String key, long increment) {
        throw new RedisSdkException("INCRBY operation not supported by current DBAPI interface");
    }

    // ---------- Hash ----------
    @Override
    public String hget(String key, String field) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withField(field);
        return asString(executeRequest(request, "hget"));
    }

    @Override
    public long hset(String key, String field, String value) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withField(field).withValue(value);
        return asLong(executeRequest(request, "hset"));
    }

    @Override
    public long hset(String key, Map<String, String> hash) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withHash(hash);
        return asLong(executeRequest(request, "hmset"));
    }

    @Override
    public long hsetnx(String key, String field, String value) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withField(field).withValue(value);
        return asLong(executeRequest(request, "hsetnx"));
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        return asStringMap(executeRequest(request, "hgetall"));
    }

    @Override
    public List<String> hmget(String key, String... fields) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withFields(Arrays.asList(fields));
        return asStringList(executeRequest(request, "hmget"));
    }

    @Override
    public long hdel(String key, String... fields) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withFields(Arrays.asList(fields));
        return asLong(executeRequest(request, "hdel"));
    }

    @Override
    public Set<String> hkeys(String key) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        return new LinkedHashSet<>(asStringList(executeRequest(request, "hkeys")));
    }

    @Override
    public List<String> hvals(String key) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        return asStringList(executeRequest(request, "hvals"));
    }

    @Override
    public boolean hexists(String key, String field) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withField(field);
        return asBoolean(executeRequest(request, "hexists"));
    }

    @Override
    public long hlen(String key) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        return asLong(executeRequest(request, "hlen"));
    }

    @Override
    public long hstrlen(String key, String field) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withField(field);
        return asLong(executeRequest(request, "hstrlen"));
    }

    @Override
    public List<String> hrandfield(String key, int count) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        request.setCount(count);
        return asStringList(executeRequest(request, "hrandfield"));
    }

    @Override
    public Map<String, String> hscan(String key, String cursor, String match, int count) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withScan(cursor, match, count);
        return asStringMap(executeRequest(request, "hscan"));
    }

    // ---------- List ----------
    @Override
    public long lpush(String key, String... values) {
        throw new RedisSdkException("LPUSH operation not supported by current DBAPI interface");
    }

    @Override
    public long rpush(String key, String... values) {
        throw new RedisSdkException("RPUSH operation not supported by current DBAPI interface");
    }

    @Override
    public String lpop(String key) {
        throw new RedisSdkException("LPOP operation not supported by current DBAPI interface");
    }

    @Override
    public String rpop(String key) {
        throw new RedisSdkException("RPOP operation not supported by current DBAPI interface");
    }

    @Override
    public List<String> lrange(String key, long start, long stop) {
        throw new RedisSdkException("LRANGE operation not supported by current DBAPI interface");
    }

    // ---------- Set ----------
    @Override
    public long sadd(String key, String... members) {
        throw new RedisSdkException("SADD operation not supported by current DBAPI interface");
    }

    @Override
    public Set<String> smembers(String key) {
        throw new RedisSdkException("SMEMBERS operation not supported by current DBAPI interface");
    }

    @Override
    public long srem(String key, String... members) {
        throw new RedisSdkException("SREM operation not supported by current DBAPI interface");
    }

    // ---------- ZSet ----------
    @Override
    public long zadd(String key, double score, String member) {
        throw new RedisSdkException("ZADD operation not supported by current DBAPI interface");
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max) {
        throw new RedisSdkException("ZRANGEBYSCORE operation not supported by current DBAPI interface");
    }

    // ---------- Key ----------
    @Override
    public boolean exists(String key) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        return asBoolean(executeRequest(request, "exists"));
    }

    @Override
    public long del(String... keys) {
        DbapiRequest request = new DbapiRequest(partition).withKeys(Arrays.asList(keys));
        return asLong(executeRequest(request, "del"));
    }

    @Override
    public long expire(String key, long seconds) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key).withSeconds(seconds);
        return asLong(executeRequest(request, "expire"));
    }

    @Override
    public long ttl(String key) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        return asLong(executeRequest(request, "ttl"));
    }

    @Override
    public long persist(String key) {
        DbapiRequest request = new DbapiRequest(partition).withKey(key);
        return asLong(executeRequest(request, "persist"));
    }

    @Override
    public List<String> scan(String cursor, String match, int count) {
        DbapiRequest request = new DbapiRequest(partition).withScan(cursor, match, count);
        return asStringList(executeRequest(request, "scan"));
    }
}
