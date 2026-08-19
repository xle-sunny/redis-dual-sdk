package com.example.redissdk.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.example.redissdk.config.RedisSdkProperties;
import com.example.redissdk.exception.RedisSdkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Nacos 配置管理器（不依赖 Spring）：
 * 1. 用业务程序提供的 Nacos 连接参数初始化 ConfigService，拉取 SDK 配置（YAML）；
 * 2. 注册 Listener 监听配置变更，解析后回调订阅者，实现灰度配置动态刷新；
 * 3. 按需初始化 NamingService，为 DBAPI Feign 通道提供服务发现能力。
 */
public class NacosConfigManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigManager.class);

    private final NacosOptions options;
    private final ConfigService configService;
    private volatile NamingService namingService;
    private volatile RedisSdkProperties current;
    private final List<Consumer<RedisSdkProperties>> listeners = new CopyOnWriteArrayList<>();
    private final Listener nacosListener;

    public NacosConfigManager(NacosOptions options) {
        this.options = options;
        try {
            this.configService = NacosFactory.createConfigService(options.toProperties());
            String content = configService.getConfig(
                    options.getDataId(), options.getGroup(), options.getConfigTimeoutMs());
            this.current = RedisSdkConfigParser.parse(content);
            this.nacosListener = new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    onConfigChanged(configInfo);
                }
            };
            configService.addListener(options.getDataId(), options.getGroup(), nacosListener);
        } catch (NacosException e) {
            throw new RedisSdkException("failed to init nacos config service, dataId="
                    + options.getDataId() + ", group=" + options.getGroup(), e);
        }
        log.info("redis-dual-sdk config loaded from nacos, dataId={}, group={}",
                options.getDataId(), options.getGroup());
    }

    private void onConfigChanged(String configInfo) {
        RedisSdkProperties newProps;
        try {
            newProps = RedisSdkConfigParser.parse(configInfo);
        } catch (Exception e) {
            log.error("invalid redis sdk config pushed from nacos, keep current config", e);
            return;
        }
        this.current = newProps;
        log.info("redis-dual-sdk config refreshed from nacos, writeChannel={}, writeGrayPercent={}, readMode={}",
                newProps.getRoute().getWriteChannel(),
                newProps.getRoute().getWriteGrayPercent(),
                newProps.getRoute().getReadMode());
        for (Consumer<RedisSdkProperties> listener : listeners) {
            try {
                listener.accept(newProps);
            } catch (Exception e) {
                log.error("redis sdk config listener error", e);
            }
        }
    }

    /** 当前生效配置（配置变更后引用会被整体替换） */
    public RedisSdkProperties current() {
        return current;
    }

    /** 订阅配置变更 */
    public void addListener(Consumer<RedisSdkProperties> listener) {
        listeners.add(listener);
    }

    /** 通过 Nacos 注册中心选择一个健康的 DBAPI 实例地址（http://host:port） */
    public String selectDbapiUrl(String serviceName) {
        try {
            NamingService naming = namingService();
            Instance instance = naming.selectOneHealthyInstance(serviceName);
            return "http://" + instance.getIp() + ":" + instance.getPort();
        } catch (NacosException e) {
            throw new RedisSdkException("failed to discover DBAPI service from nacos: " + serviceName, e);
        }
    }

    private NamingService namingService() throws NacosException {
        NamingService result = namingService;
        if (result == null) {
            synchronized (this) {
                if (namingService == null) {
                    namingService = NamingFactory.createNamingService(options.toProperties());
                }
                result = namingService;
            }
        }
        return result;
    }

    @Override
    public void close() {
        try {
            configService.removeListener(options.getDataId(), options.getGroup(), nacosListener);
            configService.shutDown();
        } catch (Exception e) {
            log.warn("shutdown nacos config service failed", e);
        }
        NamingService naming = namingService;
        if (naming != null) {
            try {
                naming.shutDown();
            } catch (Exception e) {
                log.warn("shutdown nacos naming service failed", e);
            }
        }
    }
}
