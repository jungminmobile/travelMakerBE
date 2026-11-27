package com.travelmaker.backend.place.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceTest {

    @Test
    void createPlace_success() {
        Place place = Place.builder()
                .name("협재해수욕장")
                .latitude(33.394)
                .longitude(126.2397)
                .build();

        assertThat(place.getName()).isEqualTo("협재해수욕장");
        assertThat(place.getLatitude()).isEqualTo(33.394);
        assertThat(place.getLongitude()).isEqualTo(126.2397);
    }

    @Test
    void createPlace_nameNull_throws() {
        assertThrows(NullPointerException.class, () ->
                Place.builder()
                        .latitude(33.394)
                        .longitude(126.2397)
                        .build());
    }

    @Test
    void createPlace_latitudeNull_throws() {
        assertThrows(NullPointerException.class, () ->
                Place.builder()
                        .name("협재해수욕장")
                        .longitude(126.2397)
                        .build());
    }

    @Test
    void createPlace_longitudeNull_throws() {
        assertThrows(NullPointerException.class, () ->
                Place.builder()
                        .name("협재해수욕장")
                        .latitude(33.394)
                        .build());
    }
}
