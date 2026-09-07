package com.sky.agent;

/**
 * Python Agent HTTP 调用失败。
 *
 * <p>保留失败类型、HTTP 状态码和可重试标记，避免业务层依赖
 * RestTemplate 的具体异常类型。</p>
 */
public class AgentClientException extends RuntimeException {

    public enum Reason {
        CONFLICT,
        NOT_FOUND,
        INVALID_REQUEST,
        TIMEOUT,
        UNAVAILABLE,
        REMOTE_ERROR,
        INVALID_RESPONSE,
        SERIALIZATION
    }

    private final Reason reason;
    private final Integer statusCode;
    private final boolean retryable;

    public AgentClientException(Reason reason, Integer statusCode, boolean retryable,
                                String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public Reason getReason() {
        return reason;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
