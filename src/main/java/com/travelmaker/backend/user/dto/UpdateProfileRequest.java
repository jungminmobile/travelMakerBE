package com.travelmaker.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateProfileRequest(
        String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl
) {
}
