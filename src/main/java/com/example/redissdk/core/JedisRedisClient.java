package com.example.redissdk.core;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.exception.RedisSdkException;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Connection;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Jedis 直连通道。通过 {@link UnifiedJedis} 统一封装单机（JedisPooled）与集群（JedisCluster）两种架构，
 * 上层调用完全一致，实现集群/单机模式兼容。
 *
 * 支持配置热更新：Nacos 下发新的连接配置后调用 {@link #refresh(RedisSdkProperties)} 重建连接。
 */
public class JedisRedisClient implements RedisClient, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JedisRedisClient.class);

    private volatile UnifiedJedis jedis;

    public JedisRedisClient(RedisSdkProperties properties) {
        this.jedis = build(properties);
    }

    /** Nacos 配置变更后重建底层连接（先建新、后关旧，业务无感） */
    public synchronized void refresh(RedisSdkProperties properties) {
        UnifiedJedis old = this.jedis;
        this.jedis = build(properties);
        if (old != null) {
            try {
                old.close();
            } catch (Exception e) {
                log.warn("close old jedis connection failed", e);
            }
        }
        log.info("JedisRedisClient refreshed, mode={}", properties.getMode());
    }

    private UnifiedJedis build(RedisSdkProperties p) {
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(p.getConnectionTimeoutMs())
                .socketTimeoutMillis(p.getSoTimeoutMs())
                .password(emptyToNull(p.getPassword()))
                .database(p.getMode() == RedisSdkProperties.Mode.STANDALONE ? p.getDatabase() : 0)
                .build();

        if (p.getMode() == RedisSdkProperties.Mode.CLUSTER) {
            Set<HostAndPort> nodes = new HashSet<>();
            for (String node : p.getCluster().getNodes()) {
                nodes.add(HostAndPort.from(node.trim()));
            }
            if (nodes.isEmpty()) {
                throw new RedisSdkException("redis.sdk.cluster.nodes must not be empty in CLUSTER mode");
            }
            GenericObjectPoolConfig<Connection> poolConfig = poolConfig(p);
            return new JedisCluster(nodes, clientConfig, p.getCluster().getMaxAttempts(),
                    Duration.ofMillis((long) p.getSoTimeoutMs() * p.getCluster().getMaxAttempts()), poolConfig);
        }

        HostAndPort hostAndPort = new HostAndPort(p.getStandalone().getHost(), p.getStandalone().getPort());
        return new JedisPooled(hostAndPort, clientConfig, poolConfig(p));
    }

    private ConnectionPoolConfig poolConfig(RedisSdkProperties p) {
        ConnectionPoolConfig config = new ConnectionPoolConfig();
        config.setMaxTotal(p.getPool().getMaxTotal());
        config.setMaxIdle(p.getPool().getMaxIdle());
        config.setMinIdle(p.getPool().getMinIdle());
        config.setMaxWait(Duration.ofMillis(p.getPool().getMaxWaitMs()));
        config.setTestOnBorrow(false);
        config.setTestWhileIdle(true);
        return config;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    // ---------- String ----------
    @Override
    public String get(String key) { return jedis.get(key); }

    @Override
    public String set(String key, String value) { return jedis.set(key, value); }

    @Override
    public String setex(String key, long seconds, String value) { return jedis.setex(key, seconds, value); }

    @Override
    public List<String> mget(String... keys) { return jedis.mget(keys); }

    @Override
    public String mset(String... keysValues) { return jedis.mset(keysValues); }

    @Override
    public long incrBy(String key, long increment) { return jedis.incrBy(key, increment); }

    // ---------- Hash ----------
    @Override
    public String hget(String key, String field) { return jedis.hget(key, field); }

    @Override
    public long hset(String key, String field, String value) { return jedis.hset(key, field, value); }

    @Override
    public long hset(String key, Map<String, String> hash) { return jedis.hset(key, hash); }

    @Override
    public long hsetnx(String key, String field, String value) { return jedis.hsetnx(key, field, value); }

    @Override
    public Map<String, String> hgetAll(String key) { return jedis.hgetAll(key); }

    @Override
    public List<String> hmget(String key, String... fields) { return jedis.hmget(key, fields); }

    @Override
    public long hdel(String key, String... fields) { return jedis.hdel(key, fields); }

    @Override
    public Set<String> hkeys(String key) { return jedis.hkeys(key); }

    @Override
    public List<String> hvals(String key) { return jedis.hvals(key); }

    @Override
    public boolean hexists(String key, String field) { return jedis.hexists(key, field); }

    @Override
    public long hlen(String key) { return jedis.hlen(key); }

    @Override
    public long hstrlen(String key, String field) { return jedis.hstrlen(key, field); }

    @Override
    public List<String> hrandfield(String key, int count) { return jedis.hrandfield(key, count); }

    @Override
    public Map<String, String> hscan(String key, String cursor, String match, int count) {
        // 简化实现，返回当前key的所有数据作为scan结果
        // 实际生产环境需要实现完整的scan迭代逻辑
        return new LinkedHashMap<>(jedis.hgetAll(key));
    }

    // ---------- List ----------
    @Override
    public long lpush(String key, String... values) { return jedis.lpush(key, values); }

    @Override
    public long rpush(String key, String... values) { return jedis.rpush(key, values); }

    @Override
    public String lpop(String key) { return jedis.lpop(key); }

    @Override
    public String rpop(String key) { return jedis.rpop(key); }

    @Override
    public List<String> lrange(String key, long start, long stop) { return jedis.lrange(key, start, stop); }

    // ---------- Set ----------
    @Override
    public long sadd(String key, String... members) { return jedis.sadd(key, members); }

    @Override
    public Set<String> smembers(String key) { return jedis.smembers(key); }

    @Override
    public long srem(String key, String... members) { return jedis.srem(key, members); }

    // ---------- ZSet ----------
    @Override
    public long zadd(String key, double score, String member) { return jedis.zadd(key, score, member); }

    @Override
    public List<String> zrangeByScore(String key, double min, double max) { return jedis.zrangeByScore(key, min, max); }

    // ---------- Key ----------
    @Override
    public boolean exists(String key) { return jedis.exists(key); }

    @Override
    public long del(String... keys) { return jedis.del(keys); }

    @Override
    public long expire(String key, long seconds) { return jedis.expire(key, seconds); }

    @Override
    public long ttl(String key) { return jedis.ttl(key); }

    @Override
    public long persist(String key) { return jedis.persist(key); }

    @Override
    public List<String> scan(String cursor, String match, int count) {
        // 简化实现，返回空列表
        // 实际生产环境需要实现完整的scan迭代逻辑
        return new java.util.ArrayList<>();
    }

    @Override
    public void close() {
        if (jedis != null) {
            jedis.close();
        }
    }
}
