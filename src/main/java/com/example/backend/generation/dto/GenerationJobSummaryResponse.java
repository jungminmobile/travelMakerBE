package com.example.backend.generation.dto;

import com.example.backend.generation.domain.GenerationJob;
import com.example.backend.generation.domain.GenerationJobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 일정 생성 작업 이력 목록 항목 응답.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerationJobSummaryResponse(
        @JsonProperty("job_id") UUID jobId,
        GenerationJobStatus status,
        @JsonProperty("progress_percent") Integer progressPercent,
        @JsonProperty("itinerary_id") UUID itineraryId,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("error_code") String errorCode,
        Boolean retryable,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt
) {
    public static GenerationJobSummaryResponse of(GenerationJob job, UUID itineraryId) {
        return new GenerationJobSummaryResponse(
                job.getId(),
                job.getStatus(),
                job.getProgressPercent(),
                itineraryId,
                job.getErrorMessage(),
                job.getErrorCode(),
                job.getRetryable(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
