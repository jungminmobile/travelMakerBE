package com.example.backend.generation.generator;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 일정 생성기 실행에 필요한 입력 컨텍스트.
 */
public record GenerationContext(
        UUID tripId,
        String tripTitle,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        Long budget,
        String currency,
        List<UUID> memberUserIds,
        List<Map<String, Object>> memberSnapshots,
        String specialRequests,
        boolean includeBudgetOptimization
) {
}
