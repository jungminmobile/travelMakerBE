package com.example.backend.generation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 생성기가 반환하는 일정 구조 DTO.
 */
public record GeneratedItineraryPlan(
        String title,
        String generatorName,
        List<GeneratedDay> days,
        Map<String, Object> metadata
) {
    public GeneratedItineraryPlan(String title, String generatorName, List<GeneratedDay> days) {
        this(title, generatorName, days, null);
    }

    public record GeneratedDay(
            int dayNumber,
            LocalDate date,
            String title,
            List<GeneratedItem> items
    ) {
    }

    public record GeneratedPlace(
            String externalPlaceId,
            String name,
            Double latitude,
            Double longitude,
            String category
    ) {
    }

    public record GeneratedSeparateOption(
            List<String> participantIds,
            String title,
            GeneratedPlace place,
            String description,
            String recommendationReason
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
            String recommendationReason,
            GeneratedPlace generatedPlace,
            String mode,
            List<String> pickedBy,
            List<GeneratedSeparateOption> separateOptions
    ) {
        public GeneratedItem(
                UUID placeId,
                String title,
                String description,
                LocalTime startTime,
                LocalTime endTime,
                int displayOrder,
                Long estimatedCost,
                String recommendationReason
        ) {
            this(
                    placeId,
                    title,
                    description,
                    startTime,
                    endTime,
                    displayOrder,
                    estimatedCost,
                    recommendationReason,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
