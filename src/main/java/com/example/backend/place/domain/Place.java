package com.example.backend.place.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Table(name = "places")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_place_id", nullable = true, unique = true, length = 255)
    private String externalPlaceId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "address", nullable = true, columnDefinition = "TEXT")
    private String address;

    @DecimalMin(value = "-90.0",inclusive = true ,message = "위도의 값이 -90 미만이 될 수 없습니다.")
    @DecimalMax(value = "90.0",inclusive = true, message = "위도의 값은 90을 초과할 수 없습니다.")
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @DecimalMin(value = "-180.0", inclusive = true, message = "경도는 -180 미만이 될 수 없습니다.")
    @DecimalMax(value = "180.0", inclusive = true, message = "경도는 180 초과의 값을 가질 수 없습니다.")
    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "category", nullable = true, length = 100)
    private String category;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", nullable = true, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private Place(String externalPlaceId,String name, String address, Double latitude,
                  Double longitude, String category, String description,
                  String imageUrl){
        this.externalPlaceId = externalPlaceId;
        this.name = Objects.requireNonNull(name,"name must not be null");
        this.address = address;
        this.latitude = Objects.requireNonNull(latitude,"latitude must not be null");
        this.longitude = Objects.requireNonNull(longitude,"longitude must not be null");
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    @PrePersist
    private void prePersist(){
        Instant now = Instant.now();
        if(this.createdAt == null){
            this.createdAt = now;
        }
    }
}
