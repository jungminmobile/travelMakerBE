package com.travelmaker.backend.trip.dto;

import com.travelmaker.backend.trip.domain.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvitationResponse(
        @JsonProperty("invitation_id") UUID invitationId,
        @JsonProperty("trip_id") UUID tripId,
        @JsonProperty("trip_title") String tripTitle,
        @JsonProperty("destination") String destination,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("invited_by") UUID invitedBy,
        @JsonProperty("role") TripMemberRole role,
        @JsonProperty("status")TripInvitationStatus status,
        @JsonProperty("expires_at") Instant expiresAt
        ) {
    public static InvitationResponse from(TripInvitation tripInvitation){
        Trip trip = tripInvitation.getTrip();
        return new InvitationResponse(tripInvitation.getId(), trip.getId(),
                trip.getTitle(),trip.getDestination(), trip.getStartDate(),
                trip.getEndDate(), tripInvitation.getInvitedBy(), tripInvitation.getRole(),
                tripInvitation.getStatus(), tripInvitation.getExpiresAt());
    }
}
