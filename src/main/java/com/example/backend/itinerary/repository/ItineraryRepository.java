package com.example.backend.itinerary.repository;

import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.domain.ItineraryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {
    List<Itinerary> findByTripIdOrderByVersionDesc(UUID tripId);

    Optional<Itinerary> findByTripIdAndStatus(UUID tripId, ItineraryStatus status);

    List<Itinerary> findAllByTripIdAndStatus(UUID tripId, ItineraryStatus status);

    Optional<Itinerary> findByIdAndTripId(UUID id, UUID tripId);
}
