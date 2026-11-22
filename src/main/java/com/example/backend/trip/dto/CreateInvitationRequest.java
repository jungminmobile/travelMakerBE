package com.example.backend.trip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** 초대 보내기 요청.
 * invitee_user_id 또는 invitee_email 중 하나는 필수. */
public record CreateInvitationRequest(
        @JsonProperty("invitee_user_id")UUID inviteeUserId,
        @JsonProperty("invitee_email") String inviteeEmail
        ) {}
