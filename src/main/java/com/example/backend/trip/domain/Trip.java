package com.example.backend.trip.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 그룹 여행 기본 메타데이터 엔티티 ({@code trips} 테이블).
 */
@Getter
@Entity
@Table(name = "trips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "budget")
    private Long budget;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private Trip(
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            Long budget,
            String currency,
            UUID createdBy,
            TripStatus status
    ) {
        validatePeriod(startDate, endDate);
        validateBudget(budget);
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.destination = Objects.requireNonNull(destination, "destination must not be null");
        this.startDate = startDate;
        this.endDate = endDate;
        this.budget = budget;
        this.currency = (currency == null || currency.isBlank()) ? "KRW" : currency;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.status = (status == null) ? TripStatus.PLANNING : status;
    }

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "KRW";
        }
        if (this.status == null) {
            this.status = TripStatus.PLANNING;
        }
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 여행 기본 정보를 수정합니다.
     */
    public void updateDetails(
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            Long budget,
            String currency
    ) {
        validatePeriod(startDate, endDate);
        validateBudget(budget);
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.destination = Objects.requireNonNull(destination, "destination must not be null");
        this.startDate = startDate;
        this.endDate = endDate;
        this.budget = budget;
        if (currency != null && !currency.isBlank()) {
            this.currency = currency;
        }
    }

    /**
     * 여행 진행 상태를 변경합니다.
     */
    public void changeStatus(TripStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
    }

    private static void validateBudget(Long budget) {
        if (budget != null && budget < 0) {
            throw new IllegalArgumentException("budget must not be negative");
        }
    }
}
