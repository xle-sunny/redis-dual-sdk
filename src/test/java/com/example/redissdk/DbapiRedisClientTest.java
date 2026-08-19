package com.example.redissdk;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.feign.dto.DbapiRequest;
import com.example.redissdk.feign.dto.DbapiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DbapiRedisClient 测试类
 * 测试DBAPI客户端的基本功能（简化版，不使用Mockito）
 */
public class DbapiRedisClientTest {

    private RedisSdkProperties properties;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        properties = new RedisSdkProperties();
        properties.setDbapi(new RedisSdkProperties.Dbapi());
        properties.getDbapi().setPartition(0);
        
        objectMapper = new ObjectMapper();
        
        // 创建一个简单的测试实例
        // 注意：这里不能直接创建真实的DbapiRedisClient，因为它需要真实的NacosConfigManager
        // 这个测试主要验证代码结构，实际集成测试需要真实的Nacos和DBAPI环境
    }

    @Test
    public void testRequestStructure() {
        // 测试请求对象的结构
        DbapiRequest request = new DbapiRequest(0);
        request.withKey("user:1001").withField("name").withValue("tom");
        
        assertEquals(0, request.getPartition());
        assertEquals("user:1001", request.getKey());
        assertEquals("name", request.getField());
        assertEquals("tom", request.getValue());
    }

    @Test
    public void testResponseStructure() {
        // 测试响应对象的结构
        DbapiResponse response = new DbapiResponse();
        response.setCode("OK");
        response.setMessage("OK");
        response.setSuccess(true);
        
        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
    }

    @Test
    public void testResponseWithArrayData() {
        // 测试数组数据响应（对应hmget接口）
        JsonNode data = objectMapper.createArrayNode()
                .add("26.5")
                .add("2026-08-13 10:20:30")
                .add("78.1")
                .add("2026-08-13 10:20:29");
        
        DbapiResponse response = new DbapiResponse();
        response.setCode("OK");
        response.setMessage("OK");
        response.setSuccess(true);
        response.setData(data);
        
        assertTrue(response.isSuccess());
        assertEquals(4, data.size());
        assertEquals("26.5", data.get(0).asText());
    }

    @Test
    public void testResponseWithObjectData() {
        // 测试对象数据响应（对应hgetall接口）
        JsonNode data = objectMapper.createObjectNode()
                .put("name", "tom")
                .put("age", "18");
        
        DbapiResponse response = new DbapiResponse();
        response.setCode("OK");
        response.setMessage("OK");
        response.setSuccess(true);
        response.setData(data);
        
        assertTrue(response.isSuccess());
        assertTrue(data.isObject());
        assertEquals("tom", data.get("name").asText());
    }

    @Test
    public void testErrorResponse() {
        // 测试错误响应
        DbapiResponse response = new DbapiResponse();
        response.setCode("ERROR");
        response.setMessage("Invalid key");
        response.setSuccess(false);
        
        assertFalse(response.isSuccess());
        assertEquals("ERROR", response.getCode());
    }

    @Test
    public void testPropertiesConfiguration() {
        // 测试配置属性
        RedisSdkProperties props = new RedisSdkProperties();
        props.setDbapi(new RedisSdkProperties.Dbapi());
        props.getDbapi().setPartition(0);
        props.getDbapi().setConnectTimeoutMs(1000);
        props.getDbapi().setReadTimeoutMs(2000);
        
        assertEquals(0, props.getDbapi().getPartition());
        assertEquals(1000, props.getDbapi().getConnectTimeoutMs());
        assertEquals(2000, props.getDbapi().getReadTimeoutMs());
    }

    @Test
    public void testRouteConfiguration() {
        // 测试路由配置
        RedisSdkProperties props = new RedisSdkProperties();
        props.setRoute(new RedisSdkProperties.Route());
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.DBAPI);
        props.getRoute().setReadMode(RedisSdkProperties.ReadMode.JEDIS_FIRST);
        props.getRoute().setWriteGrayPercent(50);
        
        assertEquals(RedisSdkProperties.WriteChannel.DBAPI, props.getRoute().getWriteChannel());
        assertEquals(RedisSdkProperties.ReadMode.JEDIS_FIRST, props.getRoute().getReadMode());
        assertEquals(50, props.getRoute().getWriteGrayPercent());
    }
}