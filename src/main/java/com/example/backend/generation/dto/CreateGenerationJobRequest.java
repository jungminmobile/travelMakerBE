package com.example.backend.generation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 일정 생성 요청 바디. 모든 옵션 필드는 생략 가능하다.
 */
public record CreateGenerationJobRequest(
        @JsonProperty("special_requests") String specialRequests,
        @JsonProperty("include_budget_optimization") Boolean includeBudgetOptimization
) {
}
