package com.example.backend.itinerary.repository;

import com.example.backend.itinerary.domain.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {
    List<ItineraryItem> findByItineraryDay_Itinerary_TripIdOrderByDisplayOrderAsc(UUID tripId);
}
