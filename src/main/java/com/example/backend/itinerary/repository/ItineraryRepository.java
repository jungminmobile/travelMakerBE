package com.example.backend.itinerary.repository;

import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.domain.ItineraryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {
    List<Itinerary> findByTripIdOrderByVersionDesc(UUID tripId);

    Optional<Itinerary> findByTripIdAndStatus(UUID tripId, ItineraryStatus status);

    List<Itinerary> findAllByTripIdAndStatus(UUID tripId, ItineraryStatus status);

    Optional<Itinerary> findByIdAndTripId(UUID id, UUID tripId);

    @Query("select coalesce(max(i.version), 0) from Itinerary i where i.tripId = :tripId")
    int findMaxVersionByTripId(@Param("tripId") UUID tripId);

    Optional<Itinerary> findByGenerationJobId(UUID generationJobId);

    List<Itinerary> findAllByGenerationJobIdIn(Collection<UUID> generationJobIds);
}
