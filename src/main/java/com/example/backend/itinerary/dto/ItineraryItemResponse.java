package com.example.backend.itinerary.dto;

import com.example.backend.itinerary.domain.ItineraryItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
        ItineraryPlaceResponse place,
        @JsonInclude(JsonInclude.Include.NON_NULL) String mode,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("picked_by") List<String> pickedBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> separate
) {
    @SuppressWarnings("unchecked")
    public static ItineraryItemResponse from(ItineraryItem item) {
        ItineraryPlaceResponse place = item.getPlace() == null
                ? null
                : ItineraryPlaceResponse.from(item.getPlace());

        Map<String, Object> aiMetadata = item.getAiMetadata();
        List<String> pickedBy = null;
        List<Map<String, Object>> separate = null;
        if (aiMetadata != null) {
            Object rawPickedBy = aiMetadata.get("picked_by");
            if (rawPickedBy instanceof List<?> list) {
                pickedBy = list.stream().map(Object::toString).toList();
            }
            Object rawSeparate = aiMetadata.get("separate");
            if (rawSeparate instanceof List<?> list) {
                separate = list.stream()
                        .filter(Map.class::isInstance)
                        .map(entry -> (Map<String, Object>) entry)
                        .toList();
            }
        }

        return new ItineraryItemResponse(
                item.getId(),
                item.getDisplayOrder(),
                item.getTitle(),
                item.getDescription(),
                item.getStartTime(),
                item.getEndTime(),
                item.getEstimatedCost(),
                item.getRecommendationReason(),
                place,
                item.getMode(),
                pickedBy,
                separate
        );
    }
}
