package com.example.backend.trip.dto;

import com.example.backend.trip.domain.TripMember;
import com.example.backend.trip.domain.TripMemberStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record TripMemberResponse(
        @JsonProperty("trip_id") UUID tripId,
        @JsonProperty("user_id") UUID userId,
        TripMemberStatus status
        ) {
    public static TripMemberResponse from(TripMember member){
        return new TripMemberResponse(member.getTrip().getId(), member.getUserId(), member.getStatus());
    }
}
