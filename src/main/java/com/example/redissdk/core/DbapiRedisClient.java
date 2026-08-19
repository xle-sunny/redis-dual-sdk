package com.example.redissdk.core;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.exception.RedisSdkException;
import com.example.redissdk.feign.DbapiClientFactory;
import com.example.redissdk.feign.DbapiFeignClient;
import com.example.redissdk.feign.dto.DbapiRequest;
import com.example.redissdk.nacos.NacosConfigManager;
import com.example.redissdk.feign.dto.DbapiResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DBAPI 通道：所有 Redis 命令转换为通用请求，经 Feign 调用 DBAPI 服务执行。
 * DBAPI 服务内部持有 Redis 连接（同样兼容集群/单机），SDK 侧不感知其架构。
 * Nacos 配置变更时可通过 {@link #refresh(RedisSdkProperties)} 重建 Feign 客户端。
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

    private JsonNode call(String command, String... args) {
        DbapiRequest request = new DbapiRequest(partition, command, Arrays.asList(args));
        DbapiResponse response;
        try {
            response = feignClient.execute(request);
        } catch (Exception e) {
            throw new RedisSdkException("DBAPI call failed, command=" + command, e);
        }
        if (response == null || !response.isSuccess()) {
            throw new RedisSdkException("DBAPI returned error, command=" + command
                    + ", code=" + (response == null ? "null" : response.getCode())
                    + ", msg=" + (response == null ? "null" : response.getMsg()));
        }
        return response.getData();
    }

    private static String asString(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static long asLong(JsonNode node) {
        return node == null || node.isNull() ? 0L : node.asLong();
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

    // ---------- String ----------
    @Override
    public String get(String key) { return asString(call("GET", key)); }

    @Override
    public String set(String key, String value) { return asString(call("SET", key, value)); }

    @Override
    public String setex(String key, long seconds, String value) {
        return asString(call("SETEX", key, String.valueOf(seconds), value));
    }

    @Override
    public List<String> mget(String... keys) { return asStringList(call("MGET", keys)); }

    @Override
    public String mset(String... keysValues) { return asString(call("MSET", keysValues)); }

    @Override
    public long incrBy(String key, long increment) {
        return asLong(call("INCRBY", key, String.valueOf(increment)));
    }

    // ---------- Hash ----------
    @Override
    public String hget(String key, String field) { return asString(call("HGET", key, field)); }

    @Override
    public long hset(String key, String field, String value) {
        return asLong(call("HSET", key, field, value));
    }

    @Override
    public long hset(String key, Map<String, String> hash) {
        List<String> args = new ArrayList<>(hash.size() * 2 + 1);
        args.add(key);
        for (Map.Entry<String, String> e : hash.entrySet()) {
            args.add(e.getKey());
            args.add(e.getValue());
        }
        return asLong(call("HSET", args.toArray(new String[0])));
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        JsonNode node = call("HGETALL", key);
        Map<String, String> result = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(e ->
                    result.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText()));
        }
        return result;
    }

    @Override
    public List<String> hmget(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return asStringList(call("HMGET", args));
    }

    @Override
    public long hdel(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return asLong(call("HDEL", args));
    }

    // ---------- List ----------
    @Override
    public long lpush(String key, String... values) {
        String[] args = prepend(key, values);
        return asLong(call("LPUSH", args));
    }

    @Override
    public long rpush(String key, String... values) {
        String[] args = prepend(key, values);
        return asLong(call("RPUSH", args));
    }

    @Override
    public String lpop(String key) { return asString(call("LPOP", key)); }

    @Override
    public String rpop(String key) { return asString(call("RPOP", key)); }

    @Override
    public List<String> lrange(String key, long start, long stop) {
        return asStringList(call("LRANGE", key, String.valueOf(start), String.valueOf(stop)));
    }

    // ---------- Set ----------
    @Override
    public long sadd(String key, String... members) {
        return asLong(call("SADD", prepend(key, members)));
    }

    @Override
    public Set<String> smembers(String key) {
        return new LinkedHashSet<>(asStringList(call("SMEMBERS", key)));
    }

    @Override
    public long srem(String key, String... members) {
        return asLong(call("SREM", prepend(key, members)));
    }

    // ---------- ZSet ----------
    @Override
    public long zadd(String key, double score, String member) {
        return asLong(call("ZADD", key, String.valueOf(score), member));
    }

    @Override
    public List<String> zrangeByScore(String key, double min, double max) {
        return asStringList(call("ZRANGEBYSCORE", key, String.valueOf(min), String.valueOf(max)));
    }

    // ---------- Key ----------
    @Override
    public boolean exists(String key) { return asLong(call("EXISTS", key)) > 0; }

    @Override
    public long del(String... keys) { return asLong(call("DEL", keys)); }

    @Override
    public long expire(String key, long seconds) {
        return asLong(call("EXPIRE", key, String.valueOf(seconds)));
    }

    @Override
    public long ttl(String key) { return asLong(call("TTL", key)); }

    private static String[] prepend(String key, String[] values) {
        String[] args = new String[values.length + 1];
        args[0] = key;
        System.arraycopy(values, 0, args, 1, values.length);
        return args;
    }
}
