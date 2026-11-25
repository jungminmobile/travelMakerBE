package com.example.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @JsonProperty("id_token")
        @NotBlank(message = "id_token을 입력해 주세요.")
        String idToken
) {}