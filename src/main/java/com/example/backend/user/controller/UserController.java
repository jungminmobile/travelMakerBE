package com.example.backend.user.controller;

import com.example.backend.auth.security.AuthenticatedUser;
import com.example.backend.common.ApiResponse;
import com.example.backend.user.dto.UpdateProfileRequest;
import com.example.backend.user.dto.UserResponse;
import com.example.backend.user.dto.UserSearchResponse;
import com.example.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(userService.getMyProfile(currentUser.userId()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody UpdateProfileRequest request
    ){
      return ApiResponse.ok(userService.updateMyProfile(currentUser.userId(),request));
    }

    @GetMapping("/search")
    public ApiResponse<List<UserSearchResponse>> searchUsers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam String email
    ){
        return ApiResponse.ok(userService.searchUsers(currentUser.userId(),email));
    }
}