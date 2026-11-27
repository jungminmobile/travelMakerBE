package com.travelmaker.backend.generation.dto;

import com.travelmaker.backend.generation.domain.GenerationJob;
import com.travelmaker.backend.generation.domain.GenerationJobStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 일정 생성 작업 접수 응답 (202 Accepted).
 */
public record CreateGenerationJobResponse(
        @JsonProperty("job_id") UUID jobId,
        @JsonProperty("trip_id") UUID tripId,
        GenerationJobStatus status,
        @JsonProperty("estimated_seconds") int estimatedSeconds,
        @JsonProperty("created_at") Instant createdAt
) {
    public static CreateGenerationJobResponse from(GenerationJob job, int estimatedSeconds) {
        return new CreateGenerationJobResponse(
                job.getId(),
                job.getTripId(),
                job.getStatus(),
                estimatedSeconds,
                job.getCreatedAt()
        );
    }
}
