package com.example.backend.preference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 취향 퀴즈 문항별 선택 응답 DTO.
 */
public record PreferenceAnswerDto(
        @JsonProperty("question_key") String questionKey,
        @JsonProperty("selected_option_keys") List<String> selectedOptionKeys
) {
}
