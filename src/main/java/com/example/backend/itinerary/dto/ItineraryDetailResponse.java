package com.example.backend.itinerary.dto;

import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.domain.ItineraryDay;
import com.example.backend.itinerary.domain.ItineraryItem;
import com.example.backend.itinerary.domain.ItineraryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 일정 상세 조회 응답. 일정 → 일차 → 항목 → 장소 순서로 중첩된다.
 */
public record ItineraryDetailResponse(
        @JsonProperty("itinerary_id") UUID id,
        Integer version,
        String title,
        ItineraryStatus status,
        List<ItineraryDayResponse> days
) {
    public static ItineraryDetailResponse of(Itinerary itinerary,
                                             List<ItineraryDay> days,
                                             Map<UUID, List<ItineraryItem>> itemsByDayId) {
        List<ItineraryDayResponse> dayResponses = days.stream()
                .map(day -> ItineraryDayResponse.of(day, itemsByDayId.getOrDefault(day.getId(), List.of())))
                .toList();
        return new ItineraryDetailResponse(itinerary.getId(), itinerary.getVersion(),
                itinerary.getTitle(), itinerary.getStatus(), dayResponses);
    }
}
