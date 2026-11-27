package com.travelmaker.backend.itinerary.dto;

import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.domain.ItineraryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ItinerarySummaryResponse(
        @JsonProperty("itinerary_id") UUID id, Integer version,String title,
        ItineraryStatus status, @JsonProperty("created_at") Instant createdAt
) {
    public static ItinerarySummaryResponse from(Itinerary itinerary){
        return new ItinerarySummaryResponse(itinerary.getId(), itinerary.getVersion(), itinerary.getTitle(),
                itinerary.getStatus(), itinerary.getCreatedAt());
    }
}
