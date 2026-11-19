package com.example.backend.itinerary.dto;

import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.domain.ItineraryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * 일정 확정(activate) 응답.
 */
public record ActivateItineraryResponse(
        @JsonProperty("activated_itinerary_id") UUID activatedItineraryId,
        Integer version,
        ItineraryStatus status
) {
    public static ActivateItineraryResponse from(Itinerary itinerary) {
        return new ActivateItineraryResponse(itinerary.getId(), itinerary.getVersion(), itinerary.getStatus());
    }
}
