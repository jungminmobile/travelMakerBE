package com.travelmaker.backend.preference.controller;

import com.travelmaker.backend.auth.security.AuthenticatedUser;
import com.travelmaker.backend.common.ApiResponse;
import com.travelmaker.backend.preference.dto.PreferenceProfileResponse;
import com.travelmaker.backend.preference.dto.UpsertPreferenceProfileRequest;
import com.travelmaker.backend.preference.service.PreferenceProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 취향 프로파일 조회·저장 API 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/preference-profile")
public class PreferenceProfileController {

    private final PreferenceProfileService preferenceProfileService;

    @GetMapping
    public ApiResponse<PreferenceProfileResponse> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ApiResponse.ok(preferenceProfileService.getMyProfile(currentUser.userId()));
    }

    @PutMapping
    public ApiResponse<PreferenceProfileResponse> upsertMyProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody UpsertPreferenceProfileRequest request
    ) {
        return ApiResponse.ok(preferenceProfileService.upsertMyProfile(currentUser.userId(), request));
    }
}
