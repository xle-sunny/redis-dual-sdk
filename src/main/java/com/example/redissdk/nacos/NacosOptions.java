package com.example.redissdk.nacos;

import java.util.Properties;

/**
 * Nacos 连接参数，由使用 SDK 的业务程序在初始化时提供。
 */
public class NacosOptions {

    private String serverAddr;
    private String namespace = "";
    private String username;
    private String password;

    /** SDK 配置所在 dataId，YAML 格式 */
    private String dataId = "redis-sdk.yaml";
    private String group = "DEFAULT_GROUP";
    private long configTimeoutMs = 5000;

    /** 业务方也可直接提供完整的 nacos Properties（优先级高于上面的字段） */
    private Properties rawProperties;

    public Properties toProperties() {
        if (rawProperties != null) {
            return rawProperties;
        }
        Properties p = new Properties();
        p.put("serverAddr", serverAddr == null ? "" : serverAddr);
        p.put("namespace", namespace == null ? "" : namespace);
        if (username != null && !username.isEmpty()) {
            p.put("username", username);
        }
        if (password != null && !password.isEmpty()) {
            p.put("password", password);
        }
        return p;
    }

    public String getServerAddr() { return serverAddr; }
    public void setServerAddr(String serverAddr) { this.serverAddr = serverAddr; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDataId() { return dataId; }
    public void setDataId(String dataId) { this.dataId = dataId; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public long getConfigTimeoutMs() { return configTimeoutMs; }
    public void setConfigTimeoutMs(long configTimeoutMs) { this.configTimeoutMs = configTimeoutMs; }
    public Properties getRawProperties() { return rawProperties; }
    public void setRawProperties(Properties rawProperties) { this.rawProperties = rawProperties; }
}
