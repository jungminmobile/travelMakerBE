package com.example.backend.itinerary.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Table(name = "itinerary_days")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryDay {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "title", nullable = true, length = 100)
    private String title;

    @Builder
    private ItineraryDay(Itinerary itinerary, Integer dayNumber, LocalDate date, String title){
        this.itinerary = Objects.requireNonNull(itinerary,"itinerary must not be null");
        this.dayNumber = Objects.requireNonNull(dayNumber,"daynumber must not be null");
        this.date = Objects.requireNonNull(date,"date must not be null");
        this.title = title;
    }
}
