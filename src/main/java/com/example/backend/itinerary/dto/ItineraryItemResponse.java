package com.example.backend.itinerary.dto;

import com.example.backend.itinerary.domain.ItineraryItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;
import java.util.UUID;

/**
 * 일정 상세 조회에서 일차 안에 내려가는 방문 항목.
 */
public record ItineraryItemResponse(
        @JsonProperty("item_id") UUID id,
        @JsonProperty("display_order") Integer displayOrder,
        String title,
        String description,
        @JsonProperty("start_time") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonProperty("end_time") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @JsonProperty("estimated_cost") Long estimatedCost,
        @JsonProperty("recommendation_reason") String recommendationReason,
        ItineraryPlaceResponse place
) {
    public static ItineraryItemResponse from(ItineraryItem item) {
        ItineraryPlaceResponse place = item.getPlace() == null
                ? null
                : ItineraryPlaceResponse.from(item.getPlace());
        return new ItineraryItemResponse(item.getId(), item.getDisplayOrder(), item.getTitle(),
                item.getDescription(), item.getStartTime(), item.getEndTime(),
                item.getEstimatedCost(), item.getRecommendationReason(), place);
    }
}
