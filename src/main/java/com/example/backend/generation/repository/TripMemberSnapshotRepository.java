package com.example.backend.generation.repository;

import com.example.backend.generation.domain.TripMemberSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * {@link TripMemberSnapshot} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface TripMemberSnapshotRepository extends JpaRepository<TripMemberSnapshot, UUID> {

    List<TripMemberSnapshot> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    List<TripMemberSnapshot> findByTripIdAndUserIdOrderByCreatedAtDesc(UUID tripId, UUID userId);
}
