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
 * 1:1 여행 초대 엔티티 ({@code trip_invitations} 테이블).
 */
@Getter
@Entity
@Table(
        name = "trip_invitations",
        indexes = {
                @Index(
                        name = "idx_trip_invitations_trip_invitee_status",
                        columnList = "trip_id, invitee_user_id, status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, updatable = false)
    private Trip trip;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "invitee_user_id")
    private UUID inviteeUserId;

    @Column(name = "invitee_email", length = 255)
    private String inviteeEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TripMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripInvitationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Builder
    private TripInvitation(
            Trip trip,
            UUID invitedBy,
            UUID inviteeUserId,
            String inviteeEmail,
            TripMemberRole role,
            TripInvitationStatus status,
            Instant expiresAt
    ) {
        validateInvitee(inviteeUserId, inviteeEmail);
        this.trip = Objects.requireNonNull(trip, "trip must not be null");
        this.invitedBy = Objects.requireNonNull(invitedBy, "invitedBy must not be null");
        this.inviteeUserId = inviteeUserId;
        this.inviteeEmail = (inviteeEmail != null && !inviteeEmail.isBlank()) ? inviteeEmail.trim() : null;
        this.role = (role == null) ? TripMemberRole.MEMBER : role;
        this.status = (status == null) ? TripInvitationStatus.PENDING : status;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @PrePersist
    private void prePersist() {
        validateInvitee(this.inviteeUserId, this.inviteeEmail);
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.role == null) {
            this.role = TripMemberRole.MEMBER;
        }
        if (this.status == null) {
            this.status = TripInvitationStatus.PENDING;
        }
    }

    /**
     * 초대가 만료되었는지 확인합니다.
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !this.expiresAt.isAfter(now);
    }

    /**
     * 초대를 수락합니다.
     */
    public void accept(Instant now) {
        ensurePendingAndNotExpired(now);
        this.status = TripInvitationStatus.ACCEPTED;
        this.respondedAt = now;
    }

    /**
     * 초대를 거절합니다.
     */
    public void reject(Instant now) {
        ensurePendingAndNotExpired(now);
        this.status = TripInvitationStatus.REJECTED;
        this.respondedAt = now;
    }

    /**
     * 발신자가 초대를 취소합니다.
     */
    public void cancel(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status != TripInvitationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING invitations can be canceled");
        }
        this.status = TripInvitationStatus.CANCELED;
        this.respondedAt = now;
    }

    /**
     * 초대를 만료 상태로 전환합니다.
     */
    public void expire() {
        if (this.status != TripInvitationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING invitations can be expired");
        }
        this.status = TripInvitationStatus.EXPIRED;
    }

    private void ensurePendingAndNotExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status != TripInvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not in PENDING status");
        }
        if (isExpired(now)) {
            throw new IllegalStateException("Invitation has already expired");
        }
    }

    private static void validateInvitee(UUID inviteeUserId, String inviteeEmail) {
        boolean hasUserId = inviteeUserId != null;
        boolean hasEmail = inviteeEmail != null && !inviteeEmail.isBlank();
        if (!hasUserId && !hasEmail) {
            throw new IllegalArgumentException("Either inviteeUserId or inviteeEmail must be provided");
        }
    }
}
