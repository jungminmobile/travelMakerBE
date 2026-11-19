package com.example.backend.itinerary.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "itineraries",
    uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id","version"})
)
@Entity
public class Itinerary {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trip_id",nullable = false)
    private UUID tripId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ItineraryStatus status;

    @Column(name = "generation_job_id", nullable = true)
    private UUID generationJobId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private Itinerary(UUID tripId, Integer version, String title,
                      ItineraryStatus status, UUID generationJobId,UUID createdBy){

        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.version = (version == null)?1:version;
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.generationJobId = generationJobId;
    }

    public void rename(String title) {
        this.title = Objects.requireNonNull(title, "title must not be null");
    }

    public void changeStatus(ItineraryStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    @PrePersist
    private void prePersist(){
        Instant now = Instant.now();
        if(this.createdAt == null){
            this.createdAt = now;
        }
    }
}
