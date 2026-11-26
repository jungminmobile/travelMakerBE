package com.example.backend.generation.client.dto;

/**
 * AI 서버 오류 응답 본문 ({@code { "code", "message", "retryable" }}).
 */
public record AiErrorResponse(
        String code,
        String message,
        Boolean retryable
) {
}
