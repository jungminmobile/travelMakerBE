package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)

@Getter
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false , unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url",nullable = true, columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "created_at",nullable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at",nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    public UserEntity(String email, String nickname, String profileImageUrl){
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }
}
