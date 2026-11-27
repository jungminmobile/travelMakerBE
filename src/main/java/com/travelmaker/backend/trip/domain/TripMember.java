package com.travelmaker.backend.trip.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 여행 확정 참여자 및 권한 엔티티 ({@code trip_members} 테이블).
 */
@Getter
@Entity
@Table(
        name = "trip_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_trip_members_trip_user", columnNames = {"trip_id", "user_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, updatable = false)
    private Trip trip;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TripMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripMemberStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Builder
    private TripMember(
            Trip trip,
            UUID userId,
            TripMemberRole role,
            TripMemberStatus status,
            Instant joinedAt
    ) {
        this.trip = Objects.requireNonNull(trip, "trip must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = (status == null) ? TripMemberStatus.ACTIVE : status;
        this.joinedAt = joinedAt;
    }

    @PrePersist
    private void prePersist() {
        if (this.status == null) {
            this.status = TripMemberStatus.ACTIVE;
        }
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }

    /**
     * 여행 소유자(OWNER)인지 여부를 반환합니다.
     */
    public boolean isOwner() {
        return this.role == TripMemberRole.OWNER;
    }

    /**
     * 참여자가 여행에서 나갑니다.
     */
    public void leave() {
        if (this.role == TripMemberRole.OWNER) {
            throw new IllegalStateException("OWNER cannot leave the trip without transferring ownership");
        }
        this.status = TripMemberStatus.LEFT;
    }

    /**
     * 나갔던 참여자가 다시 합류합니다.
     */
    public void rejoin(TripMemberRole role) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = TripMemberStatus.ACTIVE;
    }
}
