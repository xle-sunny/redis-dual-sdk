package com.example.redissdk.nacos;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.exception.RedisSdkException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.Map;

/**
 * 将 Nacos 下发的 YAML 配置解析为 {@link RedisSdkProperties}。
 * 支持 kebab-case（write-channel）与 camelCase（writeChannel）两种写法，枚举值大小写不敏感。
 * 配置根节点为 redis.sdk。
 */
public final class RedisSdkConfigParser {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private RedisSdkConfigParser() {
    }

    @SuppressWarnings("unchecked")
    public static RedisSdkProperties parse(String yamlContent) {
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            throw new RedisSdkException("redis sdk config from nacos is empty");
        }
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object root = yaml.load(yamlContent);
        if (!(root instanceof Map)) {
            throw new RedisSdkException("redis sdk config is not a valid yaml mapping");
        }
        Object redis = ((Map<String, Object>) root).get("redis");
        Object sdk = redis instanceof Map ? ((Map<String, Object>) redis).get("sdk") : null;
        if (!(sdk instanceof Map)) {
            // 兼容不带 redis.sdk 根节点、直接平铺的配置
            sdk = root;
        }
        try {
            return MAPPER.convertValue(sdk, RedisSdkProperties.class);
        } catch (IllegalArgumentException e) {
            throw new RedisSdkException("failed to parse redis sdk config", e);
        }
    }
}
