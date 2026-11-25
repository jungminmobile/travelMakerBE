package com.example.backend.preference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 취향 프로파일 저장/갱신(UPSERT) 요청 DTO.
 */
public record UpsertPreferenceProfileRequest(
        @JsonProperty("quiz_version") String quizVersion,
        List<PreferenceAnswerDto> answers
) {
}
