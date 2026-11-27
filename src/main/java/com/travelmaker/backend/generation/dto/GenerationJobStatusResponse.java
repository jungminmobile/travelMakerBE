package com.travelmaker.backend.generation.dto;

import com.travelmaker.backend.generation.domain.GenerationJob;
import com.travelmaker.backend.generation.domain.GenerationJobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 일정 생성 작업 단건 상태 Polling 응답.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerationJobStatusResponse(
        @JsonProperty("job_id") UUID jobId,
        GenerationJobStatus status,
        @JsonProperty("progress_percent") Integer progressPercent,
        @JsonProperty("itinerary_id") UUID itineraryId,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("error_code") String errorCode,
        Boolean retryable,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt,
        @JsonProperty("created_at") Instant createdAt
) {
    public static GenerationJobStatusResponse of(GenerationJob job, UUID itineraryId) {
        return switch (job.getStatus()) {
            case PENDING -> new GenerationJobStatusResponse(
                    job.getId(),
                    job.getStatus(),
                    job.getProgressPercent(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    job.getCreatedAt()
            );
            case RUNNING -> new GenerationJobStatusResponse(
                    job.getId(),
                    job.getStatus(),
                    job.getProgressPercent(),
                    null,
                    null,
                    null,
                    null,
                    job.getStartedAt(),
                    null,
                    null
            );
            case COMPLETED -> new GenerationJobStatusResponse(
                    job.getId(),
                    job.getStatus(),
                    job.getProgressPercent(),
                    itineraryId,
                    null,
                    null,
                    null,
                    null,
                    job.getCompletedAt(),
                    null
            );
            case FAILED -> new GenerationJobStatusResponse(
                    job.getId(),
                    job.getStatus(),
                    job.getProgressPercent(),
                    null,
                    job.getErrorMessage(),
                    job.getErrorCode(),
                    job.getRetryable(),
                    null,
                    job.getCompletedAt(),
                    null
            );
        };
    }
}
