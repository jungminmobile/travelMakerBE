package com.travelmaker.backend.generation.client;

import lombok.Getter;

/**
 * AI 서버 호출 중 발생한 오류(비즈니스 오류, LLM 한도/타임아웃, 네트워크 장애)를 표현하는 예외.
 */
@Getter
public class AiServerException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;
    private final boolean retryable;

    public AiServerException(int httpStatus, String errorCode, boolean retryable, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public AiServerException(
            int httpStatus,
            String errorCode,
            boolean retryable,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
}
