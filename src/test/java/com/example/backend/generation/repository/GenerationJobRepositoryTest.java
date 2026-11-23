package com.example.backend.generation.repository;

import com.example.backend.generation.domain.GenerationJob;
import com.example.backend.generation.domain.GenerationJobStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class GenerationJobRepositoryTest {

    @Autowired
    private GenerationJobRepository generationJobRepository;

    @Autowired
    private EntityManager entityManager;

    private GenerationJob createJob(UUID tripId) {
        return GenerationJob.builder()
                .tripId(tripId)
                .requestedBy(UUID.randomUUID())
                .inputData(Map.of(
                        "trip_id", tripId.toString(),
                        "destination", "제주도",
                        "include_budget_optimization", true
                ))
                .build();
    }

    @Test
    @DisplayName("저장 시 UUID, PENDING, 진행률 0, 생성 시각, input_data JSON이 보존된다")
    void saveAndReloadJson() {
        UUID tripId = UUID.randomUUID();
        GenerationJob saved = generationJobRepository.saveAndFlush(createJob(tripId));
        entityManager.clear();

        GenerationJob found = generationJobRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getStatus()).isEqualTo(GenerationJobStatus.PENDING);
        assertThat(found.getProgressPercent()).isEqualTo(0);
        assertThat(found.getInputData())
                .containsEntry("trip_id", tripId.toString())
                .containsEntry("destination", "제주도")
                .containsEntry("include_budget_optimization", true);
    }

    @Test
    @DisplayName("상태 전이(start -> updateProgress -> complete)와 result_data JSON이 저장된다")
    void transitionToCompleted() {
        GenerationJob job = generationJobRepository.saveAndFlush(createJob(UUID.randomUUID()));
        Instant startedAt = Instant.now();
        Instant completedAt = startedAt.plusSeconds(5);

        job.start(startedAt);
        job.updateProgress(60);
        job.complete(Map.of("itinerary_id", UUID.randomUUID().toString(), "version", 1), completedAt);
        generationJobRepository.flush();
        entityManager.clear();

        GenerationJob found = generationJobRepository.findById(job.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
        assertThat(found.getProgressPercent()).isEqualTo(100);
        assertThat(found.getStartedAt()).isNotNull();
        assertThat(found.getCompletedAt()).isNotNull();
        assertThat(found.getErrorMessage()).isNull();
        assertThat(found.getResultData()).containsEntry("version", 1);
    }

    @Test
    @DisplayName("실패 시 error_message와 FAILED 상태가 저장된다")
    void transitionToFailed() {
        GenerationJob job = generationJobRepository.saveAndFlush(createJob(UUID.randomUUID()));
        Instant now = Instant.now();
        job.start(now);
        job.fail("외부 AI 호출 타임아웃", now.plusSeconds(2));
        generationJobRepository.flush();
        entityManager.clear();

        GenerationJob found = generationJobRepository.findById(job.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
        assertThat(found.getErrorMessage()).isEqualTo("외부 AI 호출 타임아웃");
        assertThat(found.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("진행률은 0~100 범위를 벗어나면 예외가 발생한다")
    void invalidProgressThrows() {
        GenerationJob job = createJob(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> job.updateProgress(-1));
        assertThrows(IllegalArgumentException.class, () -> job.updateProgress(101));
    }

    @Test
    @DisplayName("findByIdAndTripId, 최신순 목록 조회, 진행 중 상태 존재 여부를 확인할 수 있다")
    void queryMethods() throws InterruptedException {
        UUID tripId = UUID.randomUUID();
        UUID otherTripId = UUID.randomUUID();

        GenerationJob first = generationJobRepository.saveAndFlush(createJob(tripId));
        Thread.sleep(5);
        GenerationJob second = generationJobRepository.saveAndFlush(createJob(tripId));
        generationJobRepository.saveAndFlush(createJob(otherTripId));

        assertThat(generationJobRepository.findByIdAndTripId(first.getId(), tripId)).isPresent();
        assertThat(generationJobRepository.findByIdAndTripId(first.getId(), otherTripId)).isEmpty();

        List<GenerationJob> history = generationJobRepository.findByTripIdOrderByCreatedAtDesc(tripId);
        assertThat(history).extracting(GenerationJob::getId)
                .containsExactly(second.getId(), first.getId());

        assertThat(generationJobRepository.existsByTripIdAndStatusIn(
                tripId, List.of(GenerationJobStatus.PENDING, GenerationJobStatus.RUNNING))).isTrue();

        first.complete(Map.of(), Instant.now());
        second.fail("중단", Instant.now());
        generationJobRepository.flush();

        assertThat(generationJobRepository.existsByTripIdAndStatusIn(
                tripId, List.of(GenerationJobStatus.PENDING, GenerationJobStatus.RUNNING))).isFalse();
    }
}
