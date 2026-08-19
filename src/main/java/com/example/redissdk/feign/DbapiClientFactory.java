package com.example.redissdk.feign;

import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.exception.RedisSdkException;
import com.example.redissdk.nacos.NacosConfigManager;
import feign.Feign;
import feign.Request;
import feign.RequestTemplate;
import feign.Retryer;
import feign.Target;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

import java.util.concurrent.TimeUnit;

/**
 * 构建 DBAPI Feign 客户端（原生 OpenFeign）：
 * - 配置了 {@code redis.sdk.dbapi.url}：直连该地址；
 * - 否则按 {@code redis.sdk.dbapi.service-name} 每次请求时从 Nacos 注册中心
 *   选择一个健康实例（客户端负载均衡）。
 */
public final class DbapiClientFactory {

    private DbapiClientFactory() {
    }

    public static DbapiFeignClient create(RedisSdkProperties properties, NacosConfigManager nacos) {
        RedisSdkProperties.Dbapi dbapi = properties.getDbapi();
        Feign.Builder builder = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .logger(new Slf4jLogger(DbapiFeignClient.class))
                .retryer(Retryer.NEVER_RETRY)
                .options(new Request.Options(
                        dbapi.getConnectTimeoutMs(), TimeUnit.MILLISECONDS,
                        dbapi.getReadTimeoutMs(), TimeUnit.MILLISECONDS,
                        true));

        String url = dbapi.getUrl();
        if (url != null && !url.trim().isEmpty()) {
            return builder.target(DbapiFeignClient.class, url.trim());
        }
        String serviceName = dbapi.getServiceName();
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new RedisSdkException("either redis.sdk.dbapi.url or redis.sdk.dbapi.service-name must be configured");
        }
        return builder.target(new NacosDiscoveryTarget(serviceName.trim(), nacos));
    }

    /** 每次请求时从 Nacos 选择健康实例的动态 Target */
    static class NacosDiscoveryTarget implements Target<DbapiFeignClient> {

        private final String serviceName;
        private final NacosConfigManager nacos;

        NacosDiscoveryTarget(String serviceName, NacosConfigManager nacos) {
            this.serviceName = serviceName;
            this.nacos = nacos;
        }

        @Override
        public Class<DbapiFeignClient> type() {
            return DbapiFeignClient.class;
        }

        @Override
        public String name() {
            return serviceName;
        }

        @Override
        public String url() {
            return nacos.selectDbapiUrl(serviceName);
        }

        @Override
        public Request apply(RequestTemplate input) {
            input.target(url());
            return input.request();
        }
    }
}
