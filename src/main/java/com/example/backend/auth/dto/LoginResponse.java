package com.example.backend.auth.dto;

import com.example.backend.user.dto.UserResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        @JsonProperty("is_new_user") boolean newUser,
        UserResponse user
) {}