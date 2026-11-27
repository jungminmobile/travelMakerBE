package com.travelmaker.backend.itinerary.dto;

import com.travelmaker.backend.itinerary.domain.ItineraryItem;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * 일정 항목 추가 응답.
 */
public record CreateItineraryItemResponse(
        @JsonProperty("item_id") UUID itemId,
        @JsonProperty("day_id") UUID dayId,
        String title,
        @JsonProperty("display_order") Integer displayOrder
) {
    public static CreateItineraryItemResponse from(ItineraryItem item) {
        return new CreateItineraryItemResponse(item.getId(), item.getItineraryDay().getId(),
                item.getTitle(), item.getDisplayOrder());
    }
}
