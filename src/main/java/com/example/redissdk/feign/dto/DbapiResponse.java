package com.example.redissdk.feign.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * DBAPI 通用响应体。data 为命令执行结果的 JSON 表达：
 * - GET/HGET      -> 字符串或 null
 * - MGET/HMGET    -> 字符串数组
 * - HGETALL       -> 对象(map)
 * - DEL/EXPIRE 等 -> 数字
 */
public class DbapiResponse {

    public static final int SUCCESS = 0;

    private int code;
    private String msg;
    private JsonNode data;

    public boolean isSuccess() { return code == SUCCESS; }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public JsonNode getData() { return data; }
    public void setData(JsonNode data) { this.data = data; }
}
