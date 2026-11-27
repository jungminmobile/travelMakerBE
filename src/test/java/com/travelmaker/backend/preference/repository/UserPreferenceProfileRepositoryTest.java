package com.travelmaker.backend.preference.repository;

import com.travelmaker.backend.preference.domain.UserPreferenceProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class UserPreferenceProfileRepositoryTest {

    @Autowired
    private UserPreferenceProfileRepository userPreferenceProfileRepository;

    @Autowired
    private EntityManager entityManager;

    private Map<String, Object> sampleProfileData(String optionKey) {
        return Map.of(
                "quiz_version", "v1.0",
                "answers", List.of(Map.of(
                        "question_key", "Q_DESTINATION",
                        "selected_option_keys", List.of(optionKey)
                )),
                "selected_tags", List.of(optionKey)
        );
    }

    @Test
    @DisplayName("저장 시 UUID, version 1, updated_at, profile_data JSON이 보존된다")
    void saveAndReloadJson() {
        UUID userId = UUID.randomUUID();
        UserPreferenceProfile saved = userPreferenceProfileRepository.saveAndFlush(
                UserPreferenceProfile.builder()
                        .userId(userId)
                        .profileData(sampleProfileData("MOUNTAIN"))
                        .modelName("rule-based-v1")
                        .build()
        );
        entityManager.clear();

        UserPreferenceProfile found = userPreferenceProfileRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getVersion()).isEqualTo(1);
        assertThat(found.getModelName()).isEqualTo("rule-based-v1");
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getProfileData())
                .containsEntry("quiz_version", "v1.0")
                .containsEntry("selected_tags", List.of("MOUNTAIN"));
    }

    @Test
    @DisplayName("동일 user_id로 중복 저장하면 유니크 제약 위반이 발생한다")
    void duplicateUserIdThrows() {
        UUID userId = UUID.randomUUID();
        userPreferenceProfileRepository.saveAndFlush(UserPreferenceProfile.builder()
                .userId(userId)
                .profileData(sampleProfileData("SEA"))
                .modelName("rule-based-v1")
                .build());

        UserPreferenceProfile duplicate = UserPreferenceProfile.builder()
                .userId(userId)
                .profileData(sampleProfileData("CITY"))
                .modelName("rule-based-v1")
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> userPreferenceProfileRepository.saveAndFlush(duplicate));
    }

    @Test
    @DisplayName("update 호출 시 profile_data가 교체되고 version이 1 증가한다")
    void updateIncrementsVersionAndReplacesProfileData() {
        UUID userId = UUID.randomUUID();
        UserPreferenceProfile profile = userPreferenceProfileRepository.saveAndFlush(
                UserPreferenceProfile.builder()
                        .userId(userId)
                        .profileData(sampleProfileData("MOUNTAIN"))
                        .modelName("rule-based-v1")
                        .build()
        );

        profile.update(sampleProfileData("CITY"), "rule-based-v2");
        userPreferenceProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        UserPreferenceProfile reloaded = userPreferenceProfileRepository.findByUserId(userId).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(2);
        assertThat(reloaded.getModelName()).isEqualTo("rule-based-v2");
        assertThat(reloaded.getProfileData()).containsEntry("selected_tags", List.of("CITY"));
    }

    @Test
    @DisplayName("findByUserId, existsByUserId, findAllByUserIdIn 조회가 동작한다")
    void queryMethods() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();

        userPreferenceProfileRepository.saveAndFlush(UserPreferenceProfile.builder()
                .userId(user1)
                .profileData(sampleProfileData("MOUNTAIN"))
                .modelName("rule-based-v1")
                .build());
        userPreferenceProfileRepository.saveAndFlush(UserPreferenceProfile.builder()
                .userId(user2)
                .profileData(sampleProfileData("SEA"))
                .modelName("rule-based-v1")
                .build());

        assertThat(userPreferenceProfileRepository.existsByUserId(user1)).isTrue();
        assertThat(userPreferenceProfileRepository.existsByUserId(user3)).isFalse();
        assertThat(userPreferenceProfileRepository.findByUserId(user1)).isPresent();
        assertThat(userPreferenceProfileRepository.findByUserId(user3)).isEmpty();
        assertThat(userPreferenceProfileRepository.findAllByUserIdIn(List.of(user1, user2, user3)))
                .hasSize(2)
                .extracting(UserPreferenceProfile::getUserId)
                .containsExactlyInAnyOrder(user1, user2);
    }
}
