package com.example.backend.repository;

import com.example.backend.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
public class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    void 엔티티를_생성해서_자동으로_값이_채워지나요(){
        UserEntity user = new UserEntity("knu@123","HobanWo","https://knu");
        UserEntity saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("knu@123");
    }

    @Test
    void 저장한_사용자를_id로_조회할_수_있다() {
        UserEntity saved = userRepository.save(new UserEntity("a@a.com", "nick", null));

        Optional<UserEntity> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("a@a.com");
    }

    @Test
    void 같은_이메일로_두_번_저장하면_예외가_난다() {
        userRepository.save(new UserEntity("same@a.com", "nick1", null));

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(new UserEntity("same@a.com", "nick2", null));
        });
    }
}
