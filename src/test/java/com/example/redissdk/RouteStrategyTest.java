package com.example.redissdk;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.route.RouteStrategy;
import org.junit.Test;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/**
 * RouteStrategy 测试类
 * 测试路由策略和灰度逻辑
 */
public class RouteStrategyTest {

    @Test
    public void testWriteChannelJedis() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.JEDIS);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        assertFalse(strategy.writeViaDbapi("test:key"));
    }

    @Test
    public void testWriteChannelDbapi() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.DBAPI);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        assertTrue(strategy.writeViaDbapi("test:key"));
    }

    @Test
    public void testWriteChannelGrayZeroPercent() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.GRAY);
        props.getRoute().setWriteGrayPercent(0);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        assertFalse(strategy.writeViaDbapi("test:key"));
    }

    @Test
    public void testWriteChannelGrayHundredPercent() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.GRAY);
        props.getRoute().setWriteGrayPercent(100);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        assertTrue(strategy.writeViaDbapi("test:key"));
    }

    @Test
    public void testGrayKeyPrefixes() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.JEDIS);
        props.getRoute().setGrayKeyPrefixes(Arrays.asList("order:", "user:"));
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        // 命中前缀的key应该走DBAPI
        assertTrue(strategy.writeViaDbapi("order:12345"));
        assertTrue(strategy.writeViaDbapi("user:67890"));
        
        // 不命中前缀的key应该走Jedis
        assertFalse(strategy.writeViaDbapi("product:11111"));
    }

    @Test
    public void testReadModeJedisOnly() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setReadMode(RedisSdkProperties.ReadMode.JEDIS_ONLY);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        assertFalse(strategy.readDirectDbapi("test:key"));
        assertFalse(strategy.readFallbackEnabled());
    }

    @Test
    public void testReadModeDbapiOnly() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setReadMode(RedisSdkProperties.ReadMode.DBAPI_ONLY);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        assertTrue(strategy.readDirectDbapi("test:key"));
        assertFalse(strategy.readFallbackEnabled());
    }

    @Test
    public void testReadModeJedisFirst() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setReadMode(RedisSdkProperties.ReadMode.JEDIS_FIRST);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        // 默认不直接走DBAPI
        assertFalse(strategy.readDirectDbapi("test:key"));
        // 但允许回源
        assertTrue(strategy.readFallbackEnabled());
    }

    @Test
    public void testReadGrayPercent() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setReadMode(RedisSdkProperties.ReadMode.JEDIS_FIRST);
        props.getRoute().setReadGrayPercent(100);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        // 设置100%灰度，应该直接走DBAPI
        assertTrue(strategy.readDirectDbapi("test:key"));
    }

    @Test
    public void testDeterministicHash() {
        RedisSdkProperties props = createProperties();
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.GRAY);
        props.getRoute().setWriteGrayPercent(50);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        // 同一个key的路由结果应该一致
        String key = "test:consistent:key";
        boolean result1 = strategy.writeViaDbapi(key);
        boolean result2 = strategy.writeViaDbapi(key);
        
        assertEquals(result1, result2);
    }

    @Test
    public void testDynamicConfigUpdate() {
        final RedisSdkProperties props = createProperties();
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.JEDIS);
        
        RouteStrategy strategy = new RouteStrategy(() -> props);
        
        // 初始状态：走Jedis
        assertFalse(strategy.writeViaDbapi("test:key"));
        
        // 动态更新配置
        props.getRoute().setWriteChannel(RedisSdkProperties.WriteChannel.DBAPI);
        
        // 更新后应该走DBAPI
        assertTrue(strategy.writeViaDbapi("test:key"));
    }

    private RedisSdkProperties createProperties() {
        RedisSdkProperties props = new RedisSdkProperties();
        props.setRoute(new RedisSdkProperties.Route());
        return props;
    }
}