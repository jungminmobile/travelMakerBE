package com.travelmaker.backend.trip.dto;

import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.travelmaker.backend.trip.domain.TripStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

public record TripSummaryResponse(
        @JsonProperty("trip_id") UUID tripId,
        String title, String destination,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        TripStatus status,
        @JsonProperty("my_role") TripMemberRole myRole,
        @JsonProperty("member_count") long memberCount33


) {
    public static TripSummaryResponse of(Trip trip, TripMemberRole tripMemberRole,
                                         long memberCount){
        return new TripSummaryResponse(trip.getId(), trip.getTitle(), trip.getDestination(),
                trip.getStartDate(), trip.getEndDate(),
                trip.getStatus(), tripMemberRole, memberCount
        );
    }
}
