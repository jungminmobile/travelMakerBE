package com.travelmaker.backend.trip.dto;

import com.travelmaker.backend.trip.domain.TripInvitation;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

//필드: trip_id(UUID), role(TripMemberRole)
public record AcceptInvitationResponse(
        @JsonProperty("trip_id") UUID tripId,
        TripMemberRole role
        ) {
    public static AcceptInvitationResponse from(TripInvitation tripInvitation){
        return new AcceptInvitationResponse(tripInvitation.getTrip().getId(), tripInvitation.getRole());
    }
}
