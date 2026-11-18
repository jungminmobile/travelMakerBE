package com.example.backend.place.dto;

import com.example.backend.place.domain.Place;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PlaceSearchResponse(
        @JsonProperty("place_id") UUID id,
        @JsonProperty("external_place_id") String externalPlaceId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String category,
        @JsonProperty("image_url") String imageUrl
) {
    public static PlaceSearchResponse from(Place place){
        return new PlaceSearchResponse(place.getId(),place.getExternalPlaceId(),place.getName(),
                place.getAddress(), place.getLatitude(),place.getLongitude(),
                place.getCategory(), place.getImageUrl());
    }
}
