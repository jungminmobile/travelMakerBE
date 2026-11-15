package com.example.backend.user.repository;

import com.example.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * {@link User} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface UserRepository extends JpaRepository<User, UUID> {
}
