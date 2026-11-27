package com.travelmaker.backend.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private User newUser() {
        return User.builder()
                .email("user@example.com")
                .nickname("기존 닉네임")
                .profileImageUrl("https://example.com/old.png")
                .firebaseUid("uid-1")
                .build();
    }

    @Test
    @DisplayName("프로필 수정: 닉네임과 프로필 이미지가 바뀐다")
    void updateProfileChangesNicknameAndImage() {
        User user = newUser();

        user.updateProfile("새 닉네임", "https://example.com/new.png");

        assertThat(user.getNickname()).isEqualTo("새 닉네임");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    @DisplayName("프로필 수정: 이미지를 null로 주면 이미지가 지워진다")
    void updateProfileWithNullImageClearsIt() {
        User user = newUser();

        user.updateProfile("새 닉네임", null);

        assertThat(user.getNickname()).isEqualTo("새 닉네임");
        assertThat(user.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("프로필 수정: 이미지가 이미 없어도 수정할 수 있다")
    void updateProfileWhenImageAlreadyNull() {
        User user = User.builder().email("a@a.com").nickname("닉").build();

        user.updateProfile("새 닉네임", "https://example.com/new.png");

        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    @DisplayName("프로필 수정: 닉네임이 null이면 예외이고 기존 값은 유지된다")
    void updateProfileWithNullNicknameThrows() {
        User user = newUser();

        assertThatThrownBy(() -> user.updateProfile(null, "https://example.com/new.png"))
                .isInstanceOf(NullPointerException.class);

        assertThat(user.getNickname()).isEqualTo("기존 닉네임");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/old.png");
    }

    @Test
    @DisplayName("프로필 수정: 이메일과 firebase uid는 바뀌지 않는다")
    void updateProfileKeepsEmailAndFirebaseUid() {
        User user = newUser();

        user.updateProfile("새 닉네임", null);

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getFirebaseUid()).isEqualTo("uid-1");
    }
}
