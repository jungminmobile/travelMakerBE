package com.example.backend.preference.dto;

import com.example.backend.preference.domain.UserPreferenceProfile;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 사용자 취향 프로파일 응답 DTO.
 */
public record PreferenceProfileResponse(
        @JsonProperty("profile_id") UUID profileId,
        @JsonProperty("profile_data") Map<String, Object> profileData,
        @JsonProperty("model_name") String modelName,
        Integer version,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static PreferenceProfileResponse from(UserPreferenceProfile profile) {
        return new PreferenceProfileResponse(
                profile.getId(),
                profile.getProfileData(),
                profile.getModelName(),
                profile.getVersion(),
                profile.getUpdatedAt()
        );
    }
}
