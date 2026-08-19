package com.example.redissdk.feign;

import com.example.redissdk.feign.dto.DbapiRequest;
import com.example.redissdk.feign.dto.DbapiResponse;
import feign.Headers;
import feign.RequestLine;

/**
 * DBAPI 服务 Feign 接口（原生 OpenFeign，不依赖 Spring）。
 * 由 {@link DbapiClientFactory} 构建：直连 URL 或经 Nacos 服务发现动态寻址。
 * 
 * 每个Redis命令对应独立的RESTful端点，符合实际DBAPI接口规范
 */
public interface DbapiFeignClient {

    @RequestLine("POST /api/v1/redis/hset")
    @Headers("Content-Type: application/json")
    DbapiResponse hset(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hmset")
    @Headers("Content-Type: application/json")
    DbapiResponse hmset(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hsetnx")
    @Headers("Content-Type: application/json")
    DbapiResponse hsetnx(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/del")
    @Headers("Content-Type: application/json")
    DbapiResponse del(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hdel")
    @Headers("Content-Type: application/json")
    DbapiResponse hdel(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hget")
    @Headers("Content-Type: application/json")
    DbapiResponse hget(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hmget")
    @Headers("Content-Type: application/json")
    DbapiResponse hmget(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hgetall")
    @Headers("Content-Type: application/json")
    DbapiResponse hgetAll(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hkeys")
    @Headers("Content-Type: application/json")
    DbapiResponse hkeys(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hvals")
    @Headers("Content-Type: application/json")
    DbapiResponse hvals(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/exists")
    @Headers("Content-Type: application/json")
    DbapiResponse exists(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hexists")
    @Headers("Content-Type: application/json")
    DbapiResponse hexists(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hlen")
    @Headers("Content-Type: application/json")
    DbapiResponse hlen(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hstrlen")
    @Headers("Content-Type: application/json")
    DbapiResponse hstrlen(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hrandfield")
    @Headers("Content-Type: application/json")
    DbapiResponse hrandfield(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/scan")
    @Headers("Content-Type: application/json")
    DbapiResponse scan(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/hscan")
    @Headers("Content-Type: application/json")
    DbapiResponse hscan(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/expire")
    @Headers("Content-Type: application/json")
    DbapiResponse expire(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/ttl")
    @Headers("Content-Type: application/json")
    DbapiResponse ttl(DbapiRequest request);

    @RequestLine("POST /api/v1/redis/persist")
    @Headers("Content-Type: application/json")
    DbapiResponse persist(DbapiRequest request);
}
