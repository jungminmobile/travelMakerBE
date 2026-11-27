package com.travelmaker.backend.generation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 일정 생성 요청 바디. 모든 옵션 필드는 생략 가능하다.
 */
public record CreateGenerationJobRequest(
        @JsonProperty("special_requests") String specialRequests,
        @JsonProperty("include_budget_optimization") Boolean includeBudgetOptimization,
        String destination,
        Conditions conditions,
        Options options
) {
    public CreateGenerationJobRequest(String specialRequests, Boolean includeBudgetOptimization) {
        this(specialRequests, includeBudgetOptimization, null, null, null);
    }

    public record Conditions(
            String companion,
            String transport,
            String budget,
            String tempo
    ) {
    }

    public record Options(
            @JsonProperty("max_separate_blocks_per_day") Integer maxSeparateBlocksPerDay
    ) {
    }
}
