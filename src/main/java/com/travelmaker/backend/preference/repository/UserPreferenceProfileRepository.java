package com.travelmaker.backend.preference.repository;

import com.travelmaker.backend.preference.domain.UserPreferenceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link UserPreferenceProfile} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface UserPreferenceProfileRepository extends JpaRepository<UserPreferenceProfile, UUID> {

    Optional<UserPreferenceProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<UserPreferenceProfile> findAllByUserIdIn(Collection<UUID> userIds);
}
