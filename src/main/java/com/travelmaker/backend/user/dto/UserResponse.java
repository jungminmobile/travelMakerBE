package com.travelmaker.backend.user.dto;

import com.travelmaker.backend.user.domain.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("created_at") Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getProfileImageUrl(), user.getCreatedAt());
    }
}