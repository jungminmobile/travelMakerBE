package com.example.backend.trip.dto;




import com.example.backend.trip.domain.TripInvitation;
import com.example.backend.trip.domain.TripInvitationStatus;
import com.example.backend.trip.domain.TripMemberRole;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record SentInvitationResponse(
        @JsonProperty("invitation_id") UUID invitationId,
        @JsonProperty("invitee_user_id") UUID inviteeUserId,
        @JsonProperty("invitee_email") String inviteeEmail,
        TripMemberRole role, TripInvitationStatus status,
        @JsonProperty("invited_by") UUID invitedBy,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("responded_at") Instant respondedAt
        ) {

    public static SentInvitationResponse from(TripInvitation invitation){
        return new SentInvitationResponse(invitation.getId(), invitation.getInviteeUserId(), invitation.getInviteeEmail(),
                invitation.getRole(),invitation.getStatus(),invitation.getInvitedBy(), invitation.getExpiresAt(),
                invitation.getCreatedAt(), invitation.getRespondedAt());
    }
}
