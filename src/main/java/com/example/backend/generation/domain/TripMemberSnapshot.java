package com.example.backend.generation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 일정 생성 시점의 여행 참여자 취향 프로파일 스냅샷 엔티티 ({@code trip_member_snapshots} 테이블).
 */
@Getter
@Entity
@Table(name = "trip_member_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMemberSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_snapshot", nullable = false)
    private Map<String, Object> profileSnapshot;

    @Column(name = "source_profile_version")
    private Integer sourceProfileVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private TripMemberSnapshot(
            UUID tripId,
            UUID userId,
            Map<String, Object> profileSnapshot,
            Integer sourceProfileVersion
    ) {
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(profileSnapshot, "profileSnapshot must not be null");
        this.profileSnapshot = new LinkedHashMap<>(profileSnapshot);
        this.sourceProfileVersion = sourceProfileVersion;
        this.createdAt = Instant.now();
    }

    @PrePersist
    private void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
