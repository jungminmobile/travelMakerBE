package com.example.backend.generation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 비동기 AI 일정 생성 작업 엔티티 ({@code generation_jobs} 테이블).
 */
@Getter
@Entity
@Table(name = "generation_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GenerationJobStatus status;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", nullable = false)
    private Map<String, Object> inputData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_data")
    private Map<String, Object> resultData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private GenerationJob(UUID tripId, UUID requestedBy, Map<String, Object> inputData) {
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        Objects.requireNonNull(inputData, "inputData must not be null");
        this.inputData = new LinkedHashMap<>(inputData);
        this.status = GenerationJobStatus.PENDING;
        this.progressPercent = 0;
    }

    @PrePersist
    private void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = GenerationJobStatus.PENDING;
        }
        if (this.progressPercent == null) {
            this.progressPercent = 0;
        }
    }

    /**
     * 작업을 실행 중 상태로 전환합니다.
     */
    public void start(Instant startedAt) {
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.status = GenerationJobStatus.RUNNING;
        this.progressPercent = 10;
    }

    /**
     * 작업 진행률(0~100)을 갱신합니다.
     */
    public void updateProgress(int progressPercent) {
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100");
        }
        this.progressPercent = progressPercent;
    }

    /**
     * 생성 결과를 기록하고 완료 상태로 전환합니다.
     */
    public void complete(Map<String, Object> resultData, Instant completedAt) {
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.status = GenerationJobStatus.COMPLETED;
        this.progressPercent = 100;
        this.resultData = (resultData == null) ? null : new LinkedHashMap<>(resultData);
        this.errorMessage = null;
    }

    /**
     * 실패 사유를 기록하고 실패 상태로 전환합니다.
     */
    public void fail(String errorMessage, Instant completedAt) {
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.status = GenerationJobStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
