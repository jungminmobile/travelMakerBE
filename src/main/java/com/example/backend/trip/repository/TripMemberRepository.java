package com.example.backend.trip.repository;

import com.example.backend.trip.domain.TripMember;
import com.example.backend.trip.domain.TripMemberRole;
import com.example.backend.trip.domain.TripMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link TripMember} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface TripMemberRepository extends JpaRepository<TripMember, UUID> {

    List<TripMember> findAllByTripIdAndStatus(UUID tripId, TripMemberStatus status);

    Optional<TripMember> findByTripIdAndUserId(UUID tripId, UUID userId);

    boolean existsByTripIdAndUserIdAndStatus(UUID tripId, UUID userId, TripMemberStatus status);

    Optional<TripMember> findByTripIdAndRoleAndStatus(
            UUID tripId,
            TripMemberRole role,
            TripMemberStatus status
    );
}
