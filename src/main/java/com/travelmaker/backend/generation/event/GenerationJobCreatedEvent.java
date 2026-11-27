package com.travelmaker.backend.generation.event;

import java.util.UUID;

/**
 * 일정 생성 작업 저장 커밋 후 비동기 실행을 트리거하기 위한 이벤트.
 */
public record GenerationJobCreatedEvent(UUID jobId) {
}
