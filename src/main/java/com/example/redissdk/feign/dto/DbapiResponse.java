package com.example.redissdk.feign.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * DBAPI 响应体，符合实际接口规范：
 * {"code": "OK","message": "OK","success": true,"data": ...}
 * 
 * data 为命令执行结果的 JSON 表达：
 * - HGET/HSET     -> 字符串或 null
 * - HMGET         -> 字符串数组，如 ["26.5","2026-08-13 10:20:30","78.1","2026-08-13 10:20:29"]
 * - HGETALL       -> 对象(map)
 * - HKEYS/HVALS   -> 字符串数组
 * - DEL/EXPIRE 等 -> 数字
 * - HEXISTS       -> boolean
 */
public class DbapiResponse {

    private String code;
    private String message;
    private boolean success;
    private JsonNode data;

    public boolean isSuccess() { return success && "OK".equals(code); }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isSuccessFlag() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public JsonNode getData() { return data; }
    public void setData(JsonNode data) { this.data = data; }
}
