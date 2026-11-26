package com.example.backend.trip.dto;

import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTripResponse(
        @JsonProperty("trip_id")UUID tripId, String title,
        String destination,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        Long budget, String currency, TripStatus status
        ) {
    public static UpdateTripResponse from(Trip trip){
        return new UpdateTripResponse(trip.getId(),trip.getTitle(),trip.getDestination(),
                trip.getStartDate(),trip.getEndDate(),trip.getBudget(),trip.getCurrency(), trip.getStatus());
    }
}
