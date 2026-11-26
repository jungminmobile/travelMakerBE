package com.example.backend.generation.repository;

import com.example.backend.generation.domain.TripMemberSnapshot;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class TripMemberSnapshotRepositoryTest {

    @Autowired
    private TripMemberSnapshotRepository tripMemberSnapshotRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("저장 시 UUID, created_at, source_profile_version, 백엔드 퀴즈 JSON 스냅샷이 보존된다")
    void saveAndReloadSnapshot() {
        UUID tripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> snapshotData = Map.of(
                "quiz_version", "v1.0",
                "quiz_selections", Map.of(
                        "q_food", List.of("local_market", "ramen"),
                        "q_pace", List.of("moderate")
                ),
                "has_preference", true
        );

        TripMemberSnapshot saved = tripMemberSnapshotRepository.saveAndFlush(TripMemberSnapshot.builder()
                .tripId(tripId)
                .userId(userId)
                .profileSnapshot(snapshotData)
                .sourceProfileVersion(3)
                .build());
        entityManager.clear();

        TripMemberSnapshot found = tripMemberSnapshotRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getTripId()).isEqualTo(tripId);
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getSourceProfileVersion()).isEqualTo(3);
        assertThat(found.getProfileSnapshot())
                .containsEntry("quiz_version", "v1.0")
                .containsEntry("has_preference", true);
        assertThat(found.getProfileSnapshot().get("quiz_selections")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("여행별·사용자별 스냅샷을 최신순으로 조회할 수 있다")
    void queryByTripAndUserOrderedDesc() throws InterruptedException {
        UUID tripId = UUID.randomUUID();
        UUID otherTripId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TripMemberSnapshot first = tripMemberSnapshotRepository.saveAndFlush(TripMemberSnapshot.builder()
                .tripId(tripId)
                .userId(userId)
                .profileSnapshot(Map.of("has_preference", false))
                .build());
        Thread.sleep(5);
        TripMemberSnapshot second = tripMemberSnapshotRepository.saveAndFlush(TripMemberSnapshot.builder()
                .tripId(tripId)
                .userId(userId)
                .profileSnapshot(Map.of("has_preference", true))
                .sourceProfileVersion(2)
                .build());
        tripMemberSnapshotRepository.saveAndFlush(TripMemberSnapshot.builder()
                .tripId(otherTripId)
                .userId(userId)
                .profileSnapshot(Map.of("has_preference", false))
                .build());

        List<TripMemberSnapshot> byTrip = tripMemberSnapshotRepository.findByTripIdOrderByCreatedAtDesc(tripId);
        assertThat(byTrip).extracting(TripMemberSnapshot::getId)
                .containsExactly(second.getId(), first.getId());

        List<TripMemberSnapshot> byTripAndUser =
                tripMemberSnapshotRepository.findByTripIdAndUserIdOrderByCreatedAtDesc(tripId, userId);
        assertThat(byTripAndUser).extracting(TripMemberSnapshot::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("필수 필드(tripId, userId, profileSnapshot)가 null이면 예외가 발생한다")
    void nullRequiredFieldsThrow() {
        UUID id = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> TripMemberSnapshot.builder()
                .tripId(null)
                .userId(id)
                .profileSnapshot(Map.of())
                .build());
        assertThrows(NullPointerException.class, () -> TripMemberSnapshot.builder()
                .tripId(id)
                .userId(null)
                .profileSnapshot(Map.of())
                .build());
        assertThrows(NullPointerException.class, () -> TripMemberSnapshot.builder()
                .tripId(id)
                .userId(id)
                .profileSnapshot(null)
                .build());
    }
}
