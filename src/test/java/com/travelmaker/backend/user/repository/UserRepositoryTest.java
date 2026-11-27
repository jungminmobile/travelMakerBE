package com.travelmaker.backend.user.repository;

import com.travelmaker.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("User 저장 시 UUID와 타임스탬프가 자동으로 채워진다")
    void saveUser() {
        User saved = userRepository.save(User.builder()
                .email("knu@123")
                .nickname("HobanWo")
                .profileImageUrl("https://knu")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("knu@123");
    }

    @Test
    @DisplayName("저장한 사용자를 id로 조회할 수 있다")
    void findUserById() {
        User saved = userRepository.save(User.builder()
                .email("a@a.com")
                .nickname("nick")
                .build());

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("a@a.com");
    }

    @Test
    @DisplayName("같은 이메일로 두 번 저장하면 예외가 발생한다")
    void duplicateEmailThrows() {
        userRepository.save(User.builder()
                .email("same@a.com")
                .nickname("nick1")
                .build());

        assertThrows(DataIntegrityViolationException.class, () ->
                userRepository.saveAndFlush(User.builder()
                        .email("same@a.com")
                        .nickname("nick2")
                        .build()));
    }

    @Test
    @DisplayName("이메일로 사용자를 조회할 수 있고, 없는 이메일이면 빈 Optional이다")
    void findByEmail() {
        User saved = userRepository.save(User.builder()
                .email("find@a.com")
                .nickname("nick")
                .build());

        assertThat(userRepository.findByEmail("find@a.com"))
                .map(User::getId)
                .contains(saved.getId());
        assertThat(userRepository.findByEmail("none@a.com")).isEmpty();
    }

    @Test
    @DisplayName("이메일로 사용자를 대소문자 구분 없이 조회할 수 있다")
    void findByEmailIgnoreCase() {
        User saved = userRepository.save(User.builder()
                .email("Mixed.Case@a.com")
                .nickname("nick")
                .build());

        assertThat(userRepository.findByEmailIgnoreCase("mixed.case@a.com"))
                .map(User::getId)
                .contains(saved.getId());
        assertThat(userRepository.findByEmailIgnoreCase("MIXED.CASE@A.COM")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("none@a.com")).isEmpty();
    }
}
