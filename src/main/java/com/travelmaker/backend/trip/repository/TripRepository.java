package com.travelmaker.backend.trip.repository;

import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import com.travelmaker.backend.trip.domain.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * {@link Trip} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findAllByCreatedBy(UUID createdBy);

    List<Trip> findAllByStatus(TripStatus status);

    @Query("""
            SELECT t FROM Trip t
            JOIN TripMember tm ON tm.trip = t
            WHERE tm.userId = :userId
              AND tm.status = :memberStatus
            ORDER BY t.startDate ASC
            """)
    List<Trip> findAllByMemberUserIdAndMemberStatus(
            @Param("userId") UUID userId,
            @Param("memberStatus") TripMemberStatus memberStatus
    );
}
