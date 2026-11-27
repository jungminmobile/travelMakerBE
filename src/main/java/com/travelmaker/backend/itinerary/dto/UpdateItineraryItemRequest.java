package com.travelmaker.backend.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

public record UpdateItineraryItemRequest(
        @JsonProperty("start_time") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonProperty("end_time") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @JsonProperty("estimated_cost") Long estimatedCost
) {
}
