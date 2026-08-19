package com.example.redissdk.route;

import com.example.redissdk.config.RedisSdkProperties;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.zip.CRC32;

/**
 * 路由/灰度策略：
 * 1. 写通道：JEDIS（全走 Jedis）/ DBAPI（全走 DBAPI）/ GRAY（按 key 哈希百分比灰度到 DBAPI）；
 * 2. 读模式：JEDIS_ONLY / DBAPI_ONLY / JEDIS_FIRST（Jedis 优先，可叠加读灰度百分比直接走 DBAPI）；
 * 3. key 前缀白名单：命中前缀的 key 强制走 DBAPI（便于按业务维度灰度）。
 *
 * 灰度采用 CRC32(key) % 100 的确定性哈希：同一个 key 的路由结果稳定，
 * 避免同一 key 在灰度期间读写通道抖动。
 */
public class RouteStrategy {

    /** 配置热更新时整体替换 properties 实例，因此通过 Supplier 获取当前生效配置 */
    private final Supplier<RedisSdkProperties> propertiesSupplier;

    public RouteStrategy(Supplier<RedisSdkProperties> propertiesSupplier) {
        this.propertiesSupplier = propertiesSupplier;
    }

    /** 写操作是否走 DBAPI */
    public boolean writeViaDbapi(String key) {
        RedisSdkProperties.Route route = propertiesSupplier.get().getRoute();
        if (hitPrefix(key)) {
            return true;
        }
        switch (route.getWriteChannel()) {
            case DBAPI:
                return true;
            case JEDIS:
                return false;
            case GRAY:
            default:
                return hitPercent(key, route.getWriteGrayPercent());
        }
    }

    /** 读操作是否直接走 DBAPI（不再尝试 Jedis） */
    public boolean readDirectDbapi(String key) {
        RedisSdkProperties.Route route = propertiesSupplier.get().getRoute();
        if (hitPrefix(key)) {
            return true;
        }
        switch (route.getReadMode()) {
            case DBAPI_ONLY:
                return true;
            case JEDIS_ONLY:
                return false;
            case JEDIS_FIRST:
            default:
                return hitPercent(key, route.getReadGrayPercent());
        }
    }

    /** JEDIS_FIRST 模式下，Jedis 读到的数据不全时是否允许回源 DBAPI */
    public boolean readFallbackEnabled() {
        return propertiesSupplier.get().getRoute().getReadMode() == RedisSdkProperties.ReadMode.JEDIS_FIRST;
    }

    private boolean hitPrefix(String key) {
        if (key == null) {
            return false;
        }
        for (String prefix : propertiesSupplier.get().getRoute().getGrayKeyPrefixes()) {
            if (prefix != null && !prefix.isEmpty() && key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean hitPercent(String key, int percent) {
        if (percent <= 0) {
            return false;
        }
        if (percent >= 100) {
            return true;
        }
        CRC32 crc = new CRC32();
        crc.update((key == null ? "" : key).getBytes(StandardCharsets.UTF_8));
        return crc.getValue() % 100 < percent;
    }
}
