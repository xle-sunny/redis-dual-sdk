package com.example.redissdk.feign;

import com.example.redissdk.feign.dto.DbapiRequest;
import com.example.redissdk.feign.dto.DbapiResponse;
import feign.Headers;
import feign.RequestLine;

/**
 * DBAPI 服务 Feign 接口（原生 OpenFeign，不依赖 Spring）。
 * 由 {@link DbapiClientFactory} 构建：直连 URL 或经 Nacos 服务发现动态寻址。
 */
public interface DbapiFeignClient {

    /** 通用 Redis 命令执行入口 */
    @RequestLine("POST /api/redis/execute")
    @Headers("Content-Type: application/json")
    DbapiResponse execute(DbapiRequest request);
}
