package com.example.backend.user.dto;

import com.example.backend.user.domain.User;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UserSearchResponse(
        UUID id, String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl
) {
    public static UserSearchResponse from(User user){
        return new UserSearchResponse(user.getId(),user.getNickname(),user.getProfileImageUrl());
    }
}
