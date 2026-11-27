package com.travelmaker.backend.place.repository;

import com.travelmaker.backend.place.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository extends JpaRepository<Place, UUID> {
    Optional<Place> findByExternalPlaceId(String externalPlaceId);
    List<Place> findByNameContainingIgnoreCase(String search);
}
