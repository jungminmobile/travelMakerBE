package com.travelmaker.backend.user.repository;

import com.travelmaker.backend.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class FirebaseUserRepositoryTest {
    @Autowired private UserRepository users;

    @Test
    void canFindMemberByFirebaseUid() {
        User saved = users.saveAndFlush(User.builder().firebaseUid("uid-1")
                .email("a@example.com").nickname("지원").build());
        assertThat(users.findByFirebaseUid("uid-1")).isPresent();
        assertThat(users.findByFirebaseUid("uid-1").orElseThrow().getId()).isEqualTo(saved.getId());
    }

    @Test
    void duplicateFirebaseUidIsRejected() {
        users.saveAndFlush(User.builder().firebaseUid("same-uid")
                .email("a@example.com").nickname("A").build());
        assertThatThrownBy(() -> users.saveAndFlush(User.builder().firebaseUid("same-uid")
                .email("b@example.com").nickname("B").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}