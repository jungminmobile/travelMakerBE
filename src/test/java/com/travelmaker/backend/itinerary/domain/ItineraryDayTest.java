package com.travelmaker.backend.itinerary.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItineraryDayTest {

    private final Itinerary itinerary = Itinerary.builder()
            .tripId(UUID.randomUUID())
            .title("제주 3박 4일")
            .status(ItineraryStatus.DRAFT)
            .createdBy(UUID.randomUUID())
            .build();

    private final LocalDate date = LocalDate.of(2026, 11, 19);

    @Test
    @DisplayName("필수값이 모두 있으면 생성되고 title은 비어 있어도 된다")
    void createDay_success() {
        ItineraryDay day = ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(1)
                .date(date)
                .build();

        assertThat(day.getItinerary()).isSameAs(itinerary);
        assertThat(day.getDayNumber()).isEqualTo(1);
        assertThat(day.getDate()).isEqualTo(date);
        assertThat(day.getTitle()).isNull();
    }

    @Test
    @DisplayName("itinerary가 null이면 예외가 발생한다")
    void itineraryNull_throws() {
        assertThrows(NullPointerException.class, () ->
                ItineraryDay.builder()
                        .dayNumber(1)
                        .date(date)
                        .build());
    }

    @Test
    @DisplayName("dayNumber가 null이면 예외가 발생한다")
    void dayNumberNull_throws() {
        assertThrows(NullPointerException.class, () ->
                ItineraryDay.builder()
                        .itinerary(itinerary)
                        .date(date)
                        .build());
    }

    @Test
    @DisplayName("date가 null이면 예외가 발생한다")
    void dateNull_throws() {
        assertThrows(NullPointerException.class, () ->
                ItineraryDay.builder()
                        .itinerary(itinerary)
                        .dayNumber(1)
                        .build());
    }
}
