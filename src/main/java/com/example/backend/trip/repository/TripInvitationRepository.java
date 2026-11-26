package com.example.backend.trip.repository;

import com.example.backend.trip.domain.TripInvitation;
import com.example.backend.trip.domain.TripInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link TripInvitation} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface TripInvitationRepository extends JpaRepository<TripInvitation, UUID> {

    List<TripInvitation> findAllByTripIdAndStatus(UUID tripId, TripInvitationStatus status);

    List<TripInvitation> findAllByInviteeUserIdAndStatus(UUID inviteeUserId, TripInvitationStatus status);

    List<TripInvitation> findAllByInviteeEmailAndStatus(String inviteeEmail, TripInvitationStatus status);

    List<TripInvitation> findAllByTripIdOrderByCreatedAtDesc(UUID tripId);

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

    @Query("""
        select i from TripInvitation i
        join fetch i.trip t
        where i.status = :status
            and i.expiresAt > :now
            and (i.inviteeUserId = :userId or lower(i.inviteeEmail) = lower(:email))
        order by i.createdAt desc
        """)
    List<TripInvitation> findAllValidByInviteeWithTrip(@Param("status") TripInvitationStatus status,
                                                             @Param("now") Instant now, @Param("userId") UUID userId,
                                                             @Param("email") String email);
}
