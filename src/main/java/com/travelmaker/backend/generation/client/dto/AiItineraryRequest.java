package com.travelmaker.backend.generation.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 {@code POST /itineraries} 요청 DTO (camelCase).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiItineraryRequest(
        String destination,
        String city,
        String startDate,
        String endDate,
        List<Participant> participants,
        Conditions conditions,
        Options options
) {
    /**
     * {@code quizSelections}는 숫자 인덱스로 변환하지 않고
     * 백엔드 퀴즈 응답 키({@code question_key} -> {@code selected_option_keys})를 그대로 전달한다.
     */
    public record Participant(
            String id,
            String name,
            Map<String, List<String>> quizSelections
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Conditions(
            String companion,
            String transport,
            String budget,
            String tempo
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Options(
            Integer maxSeparateBlocksPerDay
    ) {
    }
}
