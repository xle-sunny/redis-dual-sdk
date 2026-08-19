package com.example.redissdk.exception;

/** SDK 统一异常 */
public class RedisSdkException extends RuntimeException {

    public RedisSdkException(String message) {
        super(message);
    }

    public RedisSdkException(String message, Throwable cause) {
        super(message, cause);
    }
}
