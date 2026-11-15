package com.example.backend.itinerary.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItineraryTest {

    private final UUID tripId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();

    @Test
    @DisplayName("필수값이 모두 있으면 생성된다")
    void createItinerary_success() {
        Itinerary itinerary = Itinerary.builder()
                .tripId(tripId)
                .version(2)
                .title("제주 3박 4일")
                .status(ItineraryStatus.DRAFT)
                .createdBy(createdBy)
                .build();

        assertThat(itinerary.getTripId()).isEqualTo(tripId);
        assertThat(itinerary.getVersion()).isEqualTo(2);
        assertThat(itinerary.getTitle()).isEqualTo("제주 3박 4일");
        assertThat(itinerary.getStatus()).isEqualTo(ItineraryStatus.DRAFT);
        assertThat(itinerary.getCreatedBy()).isEqualTo(createdBy);
        assertThat(itinerary.getGenerationJobId()).isNull();
    }

    @Test
    @DisplayName("version을 넣지 않으면 1로 설정된다")
    void versionDefaultsToOne() {
        Itinerary itinerary = Itinerary.builder()
                .tripId(tripId)
                .title("제주 3박 4일")
                .status(ItineraryStatus.DRAFT)
                .createdBy(createdBy)
                .build();

        assertThat(itinerary.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("tripId가 null이면 예외가 발생한다")
    void tripIdNull_throws() {
        assertThrows(NullPointerException.class, () ->
                Itinerary.builder()
                        .title("제주 3박 4일")
                        .status(ItineraryStatus.DRAFT)
                        .createdBy(createdBy)
                        .build());
    }

    @Test
    @DisplayName("title이 null이면 예외가 발생한다")
    void titleNull_throws() {
        assertThrows(NullPointerException.class, () ->
                Itinerary.builder()
                        .tripId(tripId)
                        .status(ItineraryStatus.DRAFT)
                        .createdBy(createdBy)
                        .build());
    }

    @Test
    @DisplayName("status가 null이면 예외가 발생한다")
    void statusNull_throws() {
        assertThrows(NullPointerException.class, () ->
                Itinerary.builder()
                        .tripId(tripId)
                        .title("제주 3박 4일")
                        .createdBy(createdBy)
                        .build());
    }

    @Test
    @DisplayName("createdBy가 null이면 예외가 발생한다")
    void createdByNull_throws() {
        assertThrows(NullPointerException.class, () ->
                Itinerary.builder()
                        .tripId(tripId)
                        .title("제주 3박 4일")
                        .status(ItineraryStatus.DRAFT)
                        .build());
    }
}
