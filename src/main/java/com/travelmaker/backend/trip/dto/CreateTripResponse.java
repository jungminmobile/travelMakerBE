package com.travelmaker.backend.trip.dto;

import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

/** 여행 생성 응답**/

public record CreateTripResponse(
        @JsonProperty("trip_id") UUID tripId,
        String title, String destination,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        TripStatus status
) {
   public static CreateTripResponse from(Trip trip){
       return new CreateTripResponse(trip.getId(), trip.getTitle(),
               trip.getDestination(), trip.getStartDate(),trip.getEndDate(),
               trip.getStatus());
   }
}
