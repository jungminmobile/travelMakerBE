package com.example.backend.trip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**여행 생성 요청**/
public record CreateTripRequest(
        String title, String destination,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        Long budget, String currency
        ) {
}
