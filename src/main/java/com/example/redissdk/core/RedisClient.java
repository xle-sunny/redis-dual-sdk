package com.example.redissdk.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一的 Redis 操作抽象，Jedis 通道与 DBAPI 通道分别实现。
 * 业务方不直接使用本接口，统一使用 {@link RedisSdkTemplate}。
 */
public interface RedisClient {

    // ---------- String ----------
    String get(String key);

    String set(String key, String value);

    String setex(String key, long seconds, String value);

    List<String> mget(String... keys);

    String mset(String... keysValues);

    long incrBy(String key, long increment);

    // ---------- Hash ----------
    String hget(String key, String field);

    long hset(String key, String field, String value);

    long hset(String key, Map<String, String> hash);

    long hsetnx(String key, String field, String value);

    Map<String, String> hgetAll(String key);

    List<String> hmget(String key, String... fields);

    long hdel(String key, String... fields);

    Set<String> hkeys(String key);

    List<String> hvals(String key);

    boolean hexists(String key, String field);

    long hlen(String key);

    long hstrlen(String key, String field);

    List<String> hrandfield(String key, int count);

    Map<String, String> hscan(String key, String cursor, String match, int count);

    // ---------- List ----------
    long lpush(String key, String... values);

    long rpush(String key, String... values);

    String lpop(String key);

    String rpop(String key);

    List<String> lrange(String key, long start, long stop);

    // ---------- Set ----------
    long sadd(String key, String... members);

    Set<String> smembers(String key);

    long srem(String key, String... members);

    // ---------- ZSet ----------
    long zadd(String key, double score, String member);

    List<String> zrangeByScore(String key, double min, double max);

    // ---------- Key ----------
    boolean exists(String key);

    long del(String... keys);

    long expire(String key, long seconds);

    long ttl(String key);

    long persist(String key);

    List<String> scan(String cursor, String match, int count);
}
