package com.example.backend.user.controller;

import com.example.backend.auth.security.AuthenticatedUser;
import com.example.backend.common.ApiResponse;
import com.example.backend.user.dto.UserResponse;
import com.example.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(userService.getMyProfile(currentUser.userId()));
    }
}