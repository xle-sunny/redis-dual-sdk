package com.example.redissdk.feign.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DBAPI 通用 Redis 命令请求体。
 * DBAPI 服务侧按 command + args 执行对应 Redis 命令并返回结果。
 */
public class DbapiRequest {

    /** Redis 分区，本 SDK 固定为 0 分区 */
    private int partition;

    /** Redis 命令，如 GET / SET / HGETALL */
    private String command;

    /** 命令参数，按 Redis 协议顺序排列，如 SET -> [key, value] */
    private List<String> args = new ArrayList<>();

    public DbapiRequest() {
    }

    public DbapiRequest(int partition, String command, List<String> args) {
        this.partition = partition;
        this.command = command;
        this.args = args;
    }

    public int getPartition() { return partition; }
    public void setPartition(int partition) { this.partition = partition; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args; }
}
