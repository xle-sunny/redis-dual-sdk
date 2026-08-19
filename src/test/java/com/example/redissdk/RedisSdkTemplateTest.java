package com.example.redissdk;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.core.DbapiRedisClient;
import com.example.redissdk.core.JedisRedisClient;
import com.example.redissdk.core.RedisClient;
import com.example.redissdk.core.RedisSdkTemplate;
import com.example.redissdk.nacos.NacosConfigManager;
import com.example.redissdk.route.RouteStrategy;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * RedisSdkTemplate 测试类
 * 测试基本的Redis操作功能
 */
public class RedisSdkTemplateTest {

    private RedisSdkTemplate template;
    private RedisClient mockJedisClient;
    private RedisClient mockDbapiClient;
    private RouteStrategy mockRouteStrategy;

    @Before
    public void setUp() {
        // 使用mock对象进行测试
        mockJedisClient = createMockRedisClient();
        mockDbapiClient = createMockRedisClient();
        mockRouteStrategy = createMockRouteStrategy();
        
        template = new RedisSdkTemplate(mockJedisClient, mockDbapiClient, mockRouteStrategy);
    }

    @Test
    public void testHashOperations() {
        // 测试hash操作
        String key = "test:user:1001";
        
        // hset
        long setResult = template.hset(key, "name", "tom");
        assertEquals(1L, setResult);
        
        // hget
        String name = template.hget(key, "name");
        assertEquals("tom", name);
        
        // hmset
        Map<String, String> hash = new HashMap<>();
        hash.put("age", "18");
        hash.put("city", "beijing");
        long hmsetResult = template.hset(key, hash);
        assertEquals(2L, hmsetResult);
        
        // hgetAll
        Map<String, String> allData = template.hgetAll(key);
        assertNotNull(allData);
        assertTrue(allData.containsKey("name"));
        assertTrue(allData.containsKey("age"));
        
        // hmget
        List<String> values = template.hmget(key, "name", "age");
        assertNotNull(values);
        assertEquals(2, values.size());
        assertEquals("tom", values.get(0));
        
        // hdel
        long delResult = template.hdel(key, "city");
        assertEquals(1L, delResult);
    }

    @Test
    public void testNewHashOperations() {
        // 测试新增的hash操作
        String key = "test:user:1002";
        
        // hsetnx
        long setnxResult = template.hsetnx(key, "field1", "value1");
        assertTrue(setnxResult >= 0);
        
        // hkeys
        Set<String> keys = template.hkeys(key);
        assertNotNull(keys);
        
        // hvals
        List<String> vals = template.hvals(key);
        assertNotNull(vals);
        
        // hexists
        boolean exists = template.hexists(key, "field1");
        assertTrue(exists);
        
        // hlen
        long len = template.hlen(key);
        assertTrue(len >= 0);
        
        // hstrlen
        long strlen = template.hstrlen(key, "field1");
        assertTrue(strlen >= 0);
        
        // hrandfield
        List<String> randomFields = template.hrandfield(key, 1);
        assertNotNull(randomFields);
        
        // hscan
        Map<String, String> scanResult = template.hscan(key, "0", "*", 10);
        assertNotNull(scanResult);
    }

    @Test
    public void testKeyOperations() {
        // 测试key操作
        String key = "test:key:1";
        
        // exists
        boolean exists = template.exists(key);
        assertNotNull(exists);
        
        // del
        long delResult = template.del(key);
        assertTrue(delResult >= 0);
        
        // expire
        long expireResult = template.expire(key, 3600);
        assertTrue(expireResult >= 0);
        
        // ttl
        long ttl = template.ttl(key);
        assertTrue(ttl >= -2); // -2表示key不存在，-1表示没有过期时间
        
        // persist
        long persistResult = template.persist(key);
        assertTrue(persistResult >= 0);
        
        // scan
        List<String> scanResult = template.scan("0", "*", 10);
        assertNotNull(scanResult);
    }

    @Test
    public void testHgetAllWithExpectedFields() {
        // 测试带期望字段的hgetAll
        String key = "test:user:1003";
        Set<String> expectedFields = new HashSet<>(Arrays.asList("name", "age", "email"));
        
        Map<String, String> result = template.hgetAll(key, expectedFields);
        assertNotNull(result);
        
        // 如果Jedis返回的数据不完整，应该回源DBAPI
        // 这里主要测试方法存在性和基本逻辑
    }

    private RedisClient createMockRedisClient() {
        return new RedisClient() {
            @Override
            public String get(String key) {
                return "mock_value";
            }

            @Override
            public String set(String key, String value) {
                return "OK";
            }

            @Override
            public String setex(String key, long seconds, String value) {
                return "OK";
            }

            @Override
            public List<String> mget(String... keys) {
                List<String> result = new ArrayList<>();
                for (String key : keys) {
                    result.add("mock_value_" + key);
                }
                return result;
            }

            @Override
            public String mset(String... keysValues) {
                return "OK";
            }

            @Override
            public long incrBy(String key, long increment) {
                return increment;
            }

            @Override
            public String hget(String key, String field) {
                return "mock_" + field;
            }

            @Override
            public long hset(String key, String field, String value) {
                return 1L;
            }

            @Override
            public long hset(String key, Map<String, String> hash) {
                return hash.size();
            }

            @Override
            public long hsetnx(String key, String field, String value) {
                return 1L;
            }

            @Override
            public Map<String, String> hgetAll(String key) {
                Map<String, String> result = new HashMap<>();
                result.put("name", "mock_name");
                result.put("age", "18");
                return result;
            }

            @Override
            public List<String> hmget(String key, String... fields) {
                List<String> result = new ArrayList<>();
                for (String field : fields) {
                    result.add("mock_" + field);
                }
                return result;
            }

            @Override
            public long hdel(String key, String... fields) {
                return fields.length;
            }

            @Override
            public Set<String> hkeys(String key) {
                return new HashSet<>(Arrays.asList("name", "age"));
            }

            @Override
            public List<String> hvals(String key) {
                return Arrays.asList("mock_name", "18");
            }

            @Override
            public boolean hexists(String key, String field) {
                return true;
            }

            @Override
            public long hlen(String key) {
                return 2L;
            }

            @Override
            public long hstrlen(String key, String field) {
                return 10L;
            }

            @Override
            public List<String> hrandfield(String key, int count) {
                return Arrays.asList("random_field");
            }

            @Override
            public Map<String, String> hscan(String key, String cursor, String match, int count) {
                Map<String, String> result = new HashMap<>();
                result.put("scan_field", "scan_value");
                return result;
            }

            @Override
            public long lpush(String key, String... values) {
                return values.length;
            }

            @Override
            public long rpush(String key, String... values) {
                return values.length;
            }

            @Override
            public String lpop(String key) {
                return "lpop_value";
            }

            @Override
            public String rpop(String key) {
                return "rpop_value";
            }

            @Override
            public List<String> lrange(String key, long start, long stop) {
                return Arrays.asList("value1", "value2");
            }

            @Override
            public long sadd(String key, String... members) {
                return members.length;
            }

            @Override
            public Set<String> smembers(String key) {
                return new HashSet<>(Arrays.asList("member1", "member2"));
            }

            @Override
            public long srem(String key, String... members) {
                return members.length;
            }

            @Override
            public long zadd(String key, double score, String member) {
                return 1L;
            }

            @Override
            public List<String> zrangeByScore(String key, double min, double max) {
                return Arrays.asList("member1", "member2");
            }

            @Override
            public boolean exists(String key) {
                return true;
            }

            @Override
            public long del(String... keys) {
                return keys.length;
            }

            @Override
            public long expire(String key, long seconds) {
                return 1L;
            }

            @Override
            public long ttl(String key) {
                return 3600L;
            }

            @Override
            public long persist(String key) {
                return 1L;
            }

            @Override
            public List<String> scan(String cursor, String match, int count) {
                return Arrays.asList("key1", "key2");
            }
        };
    }

    private RouteStrategy createMockRouteStrategy() {
        return new RouteStrategy(() -> {
            RedisSdkProperties props = new RedisSdkProperties();
            props.setRoute(new RedisSdkProperties.Route());
            props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.JEDIS);
            props.getRoute().setReadMode(RedisSdkProperties.ReadMode.JEDIS_ONLY);
            return props;
        });
    }
}