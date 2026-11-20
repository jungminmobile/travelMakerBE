package com.example.backend.itinerary.dto;

import com.example.backend.itinerary.domain.ItineraryItem;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UpdateItineraryItemResponse(@JsonProperty("item_id") UUID itemId,boolean updated) {
    public static UpdateItineraryItemResponse from(ItineraryItem itineraryItem){
        return new UpdateItineraryItemResponse(itineraryItem.getId(), true);
    }
}
