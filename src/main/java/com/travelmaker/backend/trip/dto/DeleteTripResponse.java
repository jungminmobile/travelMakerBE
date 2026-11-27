package com.travelmaker.backend.trip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripStatus;

import java.util.UUID;

public record DeleteTripResponse(
        @JsonProperty("trip_id") UUID tripId,
        TripStatus status
        ) {
    public static DeleteTripResponse from(Trip trip){
        return new DeleteTripResponse(trip.getId(),trip.getStatus());
    }
}
