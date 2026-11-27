package com.travelmaker.backend.trip.dto;


import com.travelmaker.backend.trip.domain.TripInvitation;
import com.travelmaker.backend.trip.domain.TripInvitationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

//invitation_id, status
public record CancelInvitationResponse(
        @JsonProperty("invitation_id")UUID invitationId,
        TripInvitationStatus status) {
    public static CancelInvitationResponse from(TripInvitation tripInvitation){
        return new CancelInvitationResponse(tripInvitation.getId(), tripInvitation.getStatus());
    }
}
