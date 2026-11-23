package com.example.backend.generation.repository;

import com.example.backend.generation.domain.GenerationJob;
import com.example.backend.generation.domain.GenerationJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link GenerationJob} 엔티티용 Spring Data JPA 리포지토리.
 */
public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    Optional<GenerationJob> findByIdAndTripId(UUID id, UUID tripId);

    List<GenerationJob> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    boolean existsByTripIdAndStatusIn(UUID tripId, Collection<GenerationJobStatus> statuses);
}
