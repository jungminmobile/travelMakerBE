package com.example.backend.itinerary.dto;

import com.example.backend.place.domain.Place;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * 일정 항목 안에 중첩되어 내려가는 장소 정보.
 */
public record ItineraryPlaceResponse(
        UUID id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String category,
        @JsonProperty("image_url") String imageUrl
) {
    public static ItineraryPlaceResponse from(Place place) {
        return new ItineraryPlaceResponse(place.getId(), place.getName(), place.getAddress(),
                place.getLatitude(), place.getLongitude(), place.getCategory(), place.getImageUrl());
    }
}
