package com.ai.assistant.exception;

import lombok.Getter;

/**
 * AI服务异常
 */
@Getter
public class AiServiceException extends RuntimeException {

    private final String modelType;
    private final String errorCode;

    public AiServiceException(String message) {
        super(message);
        this.modelType = null;
        this.errorCode = null;
    }

    public AiServiceException(String modelType, String message) {
        super(message);
        this.modelType = modelType;
        this.errorCode = null;
    }

    public AiServiceException(String modelType, String errorCode, String message) {
        super(message);
        this.modelType = modelType;
        this.errorCode = errorCode;
    }

    public AiServiceException(String modelType, String message, Throwable cause) {
        super(message, cause);
        this.modelType = modelType;
        this.errorCode = null;
    }
}
