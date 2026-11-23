package com.example.backend.generation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 생성기가 반환하는 일정 구조 DTO.
 */
public record GeneratedItineraryPlan(
        String title,
        String generatorName,
        List<GeneratedDay> days
) {
    public record GeneratedDay(
            int dayNumber,
            LocalDate date,
            String title,
            List<GeneratedItem> items
    ) {
    }

    public record GeneratedItem(
            UUID placeId,
            String title,
            String description,
            LocalTime startTime,
            LocalTime endTime,
            int displayOrder,
            Long estimatedCost,
            String recommendationReason
    ) {
    }
}
