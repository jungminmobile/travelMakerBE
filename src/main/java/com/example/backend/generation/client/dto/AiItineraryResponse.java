package com.example.backend.generation.client.dto;

import java.util.List;

/**
 * AI 서버 {@code POST /itineraries} 200 응답 DTO (camelCase).
 */
public record AiItineraryResponse(
        String destination,
        String city,
        List<AiDay> days
) {
    public record AiDay(
            int day,
            String date,
            String title,
            List<AiScheduleItem> schedule
    ) {
    }

    public record AiScheduleItem(
            String time,
            String endTime,
            String title,
            String mode,
            AiPlace place,
            String description,
            String reason,
            List<String> pickedBy,
            List<AiSeparateOption> separate
    ) {
    }

    public record AiSeparateOption(
            List<String> participantIds,
            String title,
            AiPlace place,
            String description,
            String reason
    ) {
    }

    public record AiPlace(
            String id,
            String name,
            Double lat,
            Double lng,
            String concept
    ) {
    }
}
