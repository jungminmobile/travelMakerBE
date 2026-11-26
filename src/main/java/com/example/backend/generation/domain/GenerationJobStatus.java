package com.example.backend.generation.domain;

/**
 * 비동기 AI 일정 생성 작업 상태.
 */
public enum GenerationJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
