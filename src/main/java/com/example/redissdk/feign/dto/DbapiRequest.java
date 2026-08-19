package com.example.redissdk.feign.dto;

import java.util.List;
import java.util.Map;

/**
 * DBAPI Redis 命令请求体，适配具体接口参数结构。
 * 
 * 不同接口的参数结构：
 * - HGET: {"key": "user:1001", "field": "name"}
 * - HMGET: {"key": "user:1001", "fields": ["name", "age"]}
 * - HGETALL: {"key": "user:1001"}
 * - HSET: {"key": "user:1001", "field": "name", "value": "tom"}
 * - HMSET: {"key": "user:1001", "hash": {"name": "tom", "age": "18"}}
 * - DEL: {"keys": ["key1", "key2"]}
 * - EXISTS: {"key": "user:1001"}
 * - TTL: {"key": "user:1001"}
 * - EXPIRE: {"key": "user:1001", "seconds": 3600}
 * - SCAN: {"cursor": "0", "match": "*", "count": 10}
 * - HSCAN: {"key": "user:1001", "cursor": "0", "match": "*", "count": 10}
 */
public class DbapiRequest {

    /** Redis 分区，本 SDK 固定为 0 分区 */
    private int partition;

    /** 通用字段：key */
    private String key;

    /** 通用字段：field (用于 HGET/HSET/HEXISTS/HSTRLEN) */
    private String field;

    /** 通用字段：value (用于 HGET/HSET) */
    private String value;

    /** 多个key (用于 DEL/MGET) */
    private List<String> keys;

    /** 多个field (用于 HMGET/HDEL) */
    private List<String> fields;

    /** hash结构 (用于 HMSET) */
    private Map<String, String> hash;

    /** 过期时间秒数 (用于 EXPIRE) */
    private Long seconds;

    /** SCAN/HSCAN 游标 */
    private String cursor;

    /** SCAN/HSCAN 匹配模式 */
    private String match;

    /** SCAN/HSCAN 每次返回数量 */
    private Integer count;

    /** HRANDFIELD 返回数量 (复用count字段) */

    /** 扩展参数，用于兼容其他接口 */
    private Map<String, Object> params;

    public DbapiRequest() {
    }

    public DbapiRequest(int partition) {
        this.partition = partition;
    }

    public int getPartition() { return partition; }
    public void setPartition(int partition) { this.partition = partition; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public List<String> getKeys() { return keys; }
    public void setKeys(List<String> keys) { this.keys = keys; }
    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }
    public Map<String, String> getHash() { return hash; }
    public void setHash(Map<String, String> hash) { this.hash = hash; }
    public Long getSeconds() { return seconds; }
    public void setSeconds(Long seconds) { this.seconds = seconds; }
    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }
    public String getMatch() { return match; }
    public void setMatch(String match) { this.match = match; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    /** 便捷方法：设置单个key */
    public DbapiRequest withKey(String key) {
        this.key = key;
        return this;
    }

    /** 便捷方法：设置field */
    public DbapiRequest withField(String field) {
        this.field = field;
        return this;
    }

    /** 便捷方法：设置value */
    public DbapiRequest withValue(String value) {
        this.value = value;
        return this;
    }

    /** 便捷方法：设置多个keys */
    public DbapiRequest withKeys(List<String> keys) {
        this.keys = keys;
        return this;
    }

    /** 便捷方法：设置多个fields */
    public DbapiRequest withFields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    /** 便捷方法：设置hash */
    public DbapiRequest withHash(Map<String, String> hash) {
        this.hash = hash;
        return this;
    }

    /** 便捷方法：设置seconds */
    public DbapiRequest withSeconds(long seconds) {
        this.seconds = seconds;
        return this;
    }

    /** 便捷方法：设置scan参数 */
    public DbapiRequest withScan(String cursor, String match, Integer count) {
        this.cursor = cursor;
        this.match = match;
        this.count = count;
        return this;
    }
}
