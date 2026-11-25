package com.example.backend.preference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * 사용자 최종 취향 프로파일 엔티티 ({@code user_preference_profiles} 테이블).
 * MVP 범위에서는 벡터 임베딩({@code embedding}) 컬럼을 제외하고 JSONB 프로파일 데이터만 관리합니다.
 */
@Getter
@Entity
@Table(name = "user_preference_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferenceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_data", nullable = false)
    private Map<String, Object> profileData;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private UserPreferenceProfile(UUID userId, Map<String, Object> profileData, String modelName) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(profileData, "profileData must not be null");
        this.profileData = new LinkedHashMap<>(profileData);
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.version = 1;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    private void prePersist() {
        if (this.version == null) {
            this.version = 1;
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 취향 프로파일을 새 응답 데이터로 덮어쓰고 버전을 1 증가시킵니다.
     */
    public void update(Map<String, Object> profileData, String modelName) {
        Objects.requireNonNull(profileData, "profileData must not be null");
        this.profileData = new LinkedHashMap<>(profileData);
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.version = this.version + 1;
        this.updatedAt = Instant.now();
    }
}
