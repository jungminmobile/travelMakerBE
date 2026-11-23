package com.example.backend.trip.dto;

import com.example.backend.trip.domain.TripInvitation;
import com.example.backend.trip.domain.TripInvitationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

//필드: invitation_id(UUID), status(TripInvitationStatus)
public record RejectInvitationResponse(
        @JsonProperty("invitation_id")UUID invitationId,
        TripInvitationStatus status
        ) {
    public static RejectInvitationResponse from(TripInvitation tripInvitation){
        return new RejectInvitationResponse(tripInvitation.getId(), tripInvitation.getStatus());
    }
}
