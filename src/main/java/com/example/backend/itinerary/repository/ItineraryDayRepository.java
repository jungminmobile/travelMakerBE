package com.example.backend.itinerary.repository;

import com.example.backend.itinerary.domain.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {

    List<ItineraryDay> findByItineraryIdOrderByDayNumberAsc(UUID itineraryId);

    Optional<ItineraryDay> findByIdAndItineraryId(UUID id, UUID itineraryId);
}
