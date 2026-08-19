package com.example.redissdk.config;

import java.util.ArrayList;
import java.util.List;

/**
 * SDK 配置项，统一由 Nacos 配置中心下发（YAML），由 {@link com.example.redissdk.nacos.NacosConfigManager}
 * 拉取解析并监听变更实现动态刷新，不依赖 Spring。
 *
 * 示例（Nacos dataId: redis-sdk.yaml）：
 * <pre>
 * redis:
 *   sdk:
 *     enabled: true
 *     mode: cluster            # cluster / standalone
 *     standalone:
 *       host: 127.0.0.1
 *       port: 6379
 *     cluster:
 *       nodes:
 *         - 10.0.0.1:7000
 *         - 10.0.0.2:7000
 *     password: xxx
 *     database: 0              # 0 分区（单机模式生效）
 *     route:
 *       write-channel: DBAPI       # JEDIS / DBAPI / GRAY
 *       write-gray-percent: 100    # write-channel=GRAY 时生效，走 DBAPI 的流量百分比
 *       read-mode: JEDIS_FIRST     # JEDIS_ONLY / DBAPI_ONLY / JEDIS_FIRST(读补偿)
 *       read-gray-percent: 0       # read-mode=JEDIS_FIRST 时，直接走 DBAPI 的灰度百分比
 *       gray-key-prefixes:         # 命中前缀的 key 强制走 DBAPI（可选）
 *         - "order:"
 *     dbapi:
 *       service-name: dbapi-service   # Feign 从 Nacos 注册中心发现
 *       url:                          # 也可直连 URL，优先级高于 service-name
 *       partition: 0
 *       connect-timeout-ms: 1000
 *       read-timeout-ms: 2000
 * </pre>
 */
public class RedisSdkProperties {

    public enum Mode { STANDALONE, CLUSTER }

    public enum WriteChannel { JEDIS, DBAPI, GRAY }

    public enum ReadMode { JEDIS_ONLY, DBAPI_ONLY, JEDIS_FIRST }

    private boolean enabled = true;

    private Mode mode = Mode.STANDALONE;

    private Standalone standalone = new Standalone();

    private Cluster cluster = new Cluster();

    private String password;

    /** 单机模式下的 db index，需求场景固定为 0 分区 */
    private int database = 0;

    private int connectionTimeoutMs = 2000;

    private int soTimeoutMs = 2000;

    private Pool pool = new Pool();

    private Route route = new Route();

    private Dbapi dbapi = new Dbapi();

    public static class Standalone {
        private String host = "127.0.0.1";
        private int port = 6379;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class Cluster {
        /** host:port 列表 */
        private List<String> nodes = new ArrayList<>();
        private int maxAttempts = 5;

        public List<String> getNodes() { return nodes; }
        public void setNodes(List<String> nodes) { this.nodes = nodes; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    }

    public static class Pool {
        private int maxTotal = 64;
        private int maxIdle = 16;
        private int minIdle = 4;
        private long maxWaitMs = 2000;

        public int getMaxTotal() { return maxTotal; }
        public void setMaxTotal(int maxTotal) { this.maxTotal = maxTotal; }
        public int getMaxIdle() { return maxIdle; }
        public void setMaxIdle(int maxIdle) { this.maxIdle = maxIdle; }
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        public long getMaxWaitMs() { return maxWaitMs; }
        public void setMaxWaitMs(long maxWaitMs) { this.maxWaitMs = maxWaitMs; }
    }

    public static class Route {
        private WriteChannel writeChannel = WriteChannel.GRAY;
        /** 写操作走 DBAPI 的百分比（0-100），writeChannel=GRAY 时生效 */
        private int writeGrayPercent = 0;
        private ReadMode readMode = ReadMode.JEDIS_FIRST;
        /** 读操作直接走 DBAPI 的百分比（0-100），readMode=JEDIS_FIRST 时生效 */
        private int readGrayPercent = 0;
        /** 命中这些前缀的 key 强制走 DBAPI */
        private List<String> grayKeyPrefixes = new ArrayList<>();

        public WriteChannel getWriteChannel() { return writeChannel; }
        public void setWriteChannel(WriteChannel writeChannel) { this.writeChannel = writeChannel; }
        public int getWriteGrayPercent() { return writeGrayPercent; }
        public void setWriteGrayPercent(int writeGrayPercent) { this.writeGrayPercent = writeGrayPercent; }
        public ReadMode getReadMode() { return readMode; }
        public void setReadMode(ReadMode readMode) { this.readMode = readMode; }
        public int getReadGrayPercent() { return readGrayPercent; }
        public void setReadGrayPercent(int readGrayPercent) { this.readGrayPercent = readGrayPercent; }
        public List<String> getGrayKeyPrefixes() { return grayKeyPrefixes; }
        public void setGrayKeyPrefixes(List<String> grayKeyPrefixes) { this.grayKeyPrefixes = grayKeyPrefixes; }
    }

    public static class Dbapi {
        /** Nacos 注册中心中 DBAPI 服务名（负载均衡调用） */
        private String serviceName = "dbapi-service";
        /** 直连 URL（如 http://dbapi.internal:8080），配置后优先于 serviceName */
        private String url;
        private int partition = 0;
        private int connectTimeoutMs = 1000;
        private int readTimeoutMs = 2000;

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getPartition() { return partition; }
        public void setPartition(int partition) { this.partition = partition; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public Standalone getStandalone() { return standalone; }
    public void setStandalone(Standalone standalone) { this.standalone = standalone; }
    public Cluster getCluster() { return cluster; }
    public void setCluster(Cluster cluster) { this.cluster = cluster; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getDatabase() { return database; }
    public void setDatabase(int database) { this.database = database; }
    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(int connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    public int getSoTimeoutMs() { return soTimeoutMs; }
    public void setSoTimeoutMs(int soTimeoutMs) { this.soTimeoutMs = soTimeoutMs; }
    public Pool getPool() { return pool; }
    public void setPool(Pool pool) { this.pool = pool; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public Dbapi getDbapi() { return dbapi; }
    public void setDbapi(Dbapi dbapi) { this.dbapi = dbapi; }

    /** Redis 连接相关配置指纹：变化时需要重建 Jedis 连接 */
    public String connectionFingerprint() {
        return mode + "|" + standalone.getHost() + ":" + standalone.getPort()
                + "|" + String.join(",", cluster.getNodes()) + "|" + cluster.getMaxAttempts()
                + "|" + password + "|" + database
                + "|" + connectionTimeoutMs + "|" + soTimeoutMs
                + "|" + pool.getMaxTotal() + "," + pool.getMaxIdle() + "," + pool.getMinIdle() + "," + pool.getMaxWaitMs();
    }

    /** DBAPI(Feign) 连接相关配置指纹：变化时需要重建 Feign 客户端 */
    public String dbapiFingerprint() {
        return dbapi.getServiceName() + "|" + dbapi.getUrl()
                + "|" + dbapi.getConnectTimeoutMs() + "|" + dbapi.getReadTimeoutMs();
    }
}
