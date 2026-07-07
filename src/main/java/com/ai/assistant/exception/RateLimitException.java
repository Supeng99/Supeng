package com.ai.assistant.exception;

import lombok.Getter;

/**
 * 限流异常
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final long retryAfter;

    public RateLimitException() {
        super("请求过于频繁，请稍后再试");
        this.retryAfter = 60;
    }

    public RateLimitException(long retryAfter) {
        super("请求过于频繁，请" + retryAfter + "秒后重试");
        this.retryAfter = retryAfter;
    }

    public RateLimitException(String message, long retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
