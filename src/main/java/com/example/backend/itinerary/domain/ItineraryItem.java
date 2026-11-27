package com.example.backend.itinerary.domain;


import com.example.backend.place.domain.Place;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Table(name = "itinerary_items")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay itineraryDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = true)
    private Place place;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = true)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = true)
    private LocalTime endTime;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Min(value = 0, message = "cost must not be smaller than 0")
    @Column(name = "estimated_cost", nullable = true)
    private Long estimatedCost; //엔화이므로 소수점 이하로 안됨

    @Column(name = "recommendation_reason", nullable = true, columnDefinition = "TEXT")
    private String recommendationReason;

    @Column(name = "mode", nullable = true, length = 20)
    private String mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_metadata", nullable = true)
    private Map<String, Object> aiMetadata;

    @Builder
    private ItineraryItem(ItineraryDay itineraryDay, Place place, String title, String description,
                          LocalTime startTime, LocalTime endTime, Integer displayOrder,
                          Long estimatedCost, String recommendationReason,
                          String mode, Map<String, Object> aiMetadata){
        this.itineraryDay = Objects.requireNonNull(itineraryDay,"Not allow null");
        this.place = place;
        this.title = Objects.requireNonNull(title,"Not allow null");
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.displayOrder = Objects.requireNonNull(displayOrder,"Not allow null");
        this.estimatedCost = (estimatedCost == null) ? 0L : estimatedCost;
        this.recommendationReason = recommendationReason;
        this.mode = mode;
        this.aiMetadata = (aiMetadata == null) ? null : new LinkedHashMap<>(aiMetadata);
    }

    public void update(LocalTime startTime, LocalTime endTime, Integer displayOrder, Long estimatedCost){
        this.startTime = startTime;
        this.endTime = endTime;
        this.displayOrder = Objects.requireNonNull(displayOrder,"Not allow null");
        this.estimatedCost = estimatedCost;
    }
}
