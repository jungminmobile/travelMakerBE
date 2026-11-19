package com.example.backend.itinerary.dto;

import com.example.backend.itinerary.domain.ItineraryDay;
import com.example.backend.itinerary.domain.ItineraryItem;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 일정 상세 조회에서 내려가는 하루치 일정과 그날의 항목 목록.
 */
public record ItineraryDayResponse(
        @JsonProperty("day_id") UUID id,
        @JsonProperty("day_number") Integer dayNumber,
        LocalDate date,
        String title,
        List<ItineraryItemResponse> items
) {
    public static ItineraryDayResponse of(ItineraryDay day, List<ItineraryItem> items) {
        return new ItineraryDayResponse(day.getId(), day.getDayNumber(), day.getDate(), day.getTitle(),
                items.stream().map(ItineraryItemResponse::from).toList());
    }
}
