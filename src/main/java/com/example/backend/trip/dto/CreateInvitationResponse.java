package com.example.backend.trip.dto;

import com.example.backend.trip.domain.TripInvitation;
import com.example.backend.trip.domain.TripInvitationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record CreateInvitationResponse(
        @JsonProperty("invitation_id") UUID invitationId,
        @JsonProperty("trip_id") UUID tripId,
        @JsonProperty("invitee_user_id") UUID inviteeUserId,
        @JsonProperty("invitee_email") String inviteeEmail,
        TripInvitationStatus status,
        @JsonProperty("expires_at") Instant expiresAt
        ) {
    public static CreateInvitationResponse from(TripInvitation tripInvitation){
        return new CreateInvitationResponse(tripInvitation.getId(),tripInvitation.getTrip().getId(),
                tripInvitation.getInviteeUserId(),tripInvitation.getInviteeEmail(),
                tripInvitation.getStatus(), tripInvitation.getExpiresAt());
    }
}
