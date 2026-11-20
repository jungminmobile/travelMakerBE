package com.example.backend.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;
import java.util.UUID;

/**
 * 일정 항목 추가 요청. title과 display_order는 필수이고 나머지는 생략할 수 있다.
 */
public record CreateItineraryItemRequest(
        @JsonProperty("place_id") UUID placeId,
        String title,
        String description,
        @JsonProperty("start_time") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonProperty("end_time") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @JsonProperty("display_order") Integer displayOrder,
        @JsonProperty("estimated_cost") Long estimatedCost
) {
}
