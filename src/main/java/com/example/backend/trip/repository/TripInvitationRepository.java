package com.example.backend.trip.repository;

import com.example.backend.trip.domain.TripInvitation;
import com.example.backend.trip.domain.TripInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * {@link TripInvitation} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface TripInvitationRepository extends JpaRepository<TripInvitation, UUID> {

    List<TripInvitation> findAllByTripIdAndStatus(UUID tripId, TripInvitationStatus status);

    List<TripInvitation> findAllByInviteeUserIdAndStatus(UUID inviteeUserId, TripInvitationStatus status);

    List<TripInvitation> findAllByInviteeEmailAndStatus(String inviteeEmail, TripInvitationStatus status);

    boolean existsByTripIdAndInviteeUserIdAndStatus(
            UUID tripId,
            UUID inviteeUserId,
            TripInvitationStatus status
    );

    boolean existsByTripIdAndInviteeEmailAndStatus(
            UUID tripId,
            String inviteeEmail,
            TripInvitationStatus status
    );
}
