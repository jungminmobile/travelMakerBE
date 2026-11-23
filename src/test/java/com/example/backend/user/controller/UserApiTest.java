package com.example.backend.user.controller;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.auth.security.FirebaseTokenVerifier;
import com.example.backend.user.domain.User;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 프로필 수정(PATCH /users/me), 사용자 검색(GET /users/search) API 테스트.
 */
@SpringBootTest
class UserApiTest {

    private static final String AUTH_HEADER = "Bearer valid-token";
    private static final String ME = "/api/v1/users/me";
    private static final String SEARCH = "/api/v1/users/search";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;

    @MockitoBean
    private FirebaseTokenVerifier verifier;

    private MockMvc mvc;
    private User me;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        me = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-me")
                .email("me@example.com")
                .nickname("기존 닉네임")
                .profileImageUrl("https://example.com/old.png")
                .build());
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-me", "me@example.com", "기존 닉네임", null));
    }

    // 다른 테스트 클래스가 같은 DB를 쓰므로 항상 비운다.
    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private User saveUser(String uid, String email, String nickname, String profileImageUrl) {
        return userRepository.saveAndFlush(User.builder()
                .firebaseUid(uid)
                .email(email)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .build());
    }

    private ResultActions patchMe(String body) throws Exception {
        return mvc.perform(patch(ME)
                .header("Authorization", AUTH_HEADER)
                .contentType("application/json")
                .content(body));
    }

    private ResultActions search(String email) throws Exception {
        return mvc.perform(get(SEARCH)
                .header("Authorization", AUTH_HEADER)
                .param("email", email));
    }

    private User reloadMe() {
        return userRepository.findById(me.getId()).orElseThrow();
    }

    // ---------------------------------------------------------------- 내 프로필 수정 (PATCH)

    @Test
    @DisplayName("수정: 200과 함께 닉네임·이미지가 바뀌고 이메일·가입일은 그대로이며 내 정보 조회에도 반영된다")
    void updateMyProfile() throws Exception {
        patchMe("{\"nickname\":\"새 닉네임\",\"profile_image_url\":\"https://example.com/new.png\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(me.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("me@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("새 닉네임"))
                .andExpect(jsonPath("$.data.profile_image_url").value("https://example.com/new.png"))
                .andExpect(jsonPath("$.data.created_at").exists());

        User saved = reloadMe();
        assertThat(saved.getNickname()).isEqualTo("새 닉네임");
        assertThat(saved.getProfileImageUrl()).isEqualTo("https://example.com/new.png");
        assertThat(saved.getEmail()).isEqualTo("me@example.com");
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(me.getUpdatedAt());

        mvc.perform(get(ME).header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새 닉네임"))
                .andExpect(jsonPath("$.data.profile_image_url").value("https://example.com/new.png"));
    }

    @Test
    @DisplayName("수정: 닉네임 앞뒤 공백은 잘려서 저장된다")
    void updateMyProfileTrimsNickname() throws Exception {
        patchMe("{\"nickname\":\"  새 닉네임  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새 닉네임"));

        assertThat(reloadMe().getNickname()).isEqualTo("새 닉네임");
    }

    @Test
    @DisplayName("수정: 이미지를 보내지 않거나 null·공백이면 이미지가 지워진다")
    void updateMyProfileClearsImage() throws Exception {
        patchMe("{\"nickname\":\"새 닉네임\"}")
                .andExpect(status().isOk());
        assertThat(reloadMe().getProfileImageUrl()).isNull();

        // 다시 이미지를 넣은 뒤 null, 공백 문자열로도 지워지는지 확인
        patchMe("{\"nickname\":\"새 닉네임\",\"profile_image_url\":\"https://example.com/a.png\"}")
                .andExpect(status().isOk());
        patchMe("{\"nickname\":\"새 닉네임\",\"profile_image_url\":null}")
                .andExpect(status().isOk());
        assertThat(reloadMe().getProfileImageUrl()).isNull();

        patchMe("{\"nickname\":\"새 닉네임\",\"profile_image_url\":\"https://example.com/a.png\"}")
                .andExpect(status().isOk());
        patchMe("{\"nickname\":\"새 닉네임\",\"profile_image_url\":\"   \"}")
                .andExpect(status().isOk());
        assertThat(reloadMe().getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("수정: 닉네임은 50자까지 허용된다")
    void updateMyProfileAllowsFiftyCharacters() throws Exception {
        patchMe("{\"nickname\":\"" + "가".repeat(50) + "\"}")
                .andExpect(status().isOk());

        assertThat(reloadMe().getNickname()).hasSize(50);
    }

    @Test
    @DisplayName("수정: 닉네임이 없거나 공백이거나 50자를 넘으면 400이고 기존 값이 유지된다")
    void updateMyProfileWithInvalidNicknameIs400() throws Exception {
        patchMe("{\"profile_image_url\":\"https://example.com/new.png\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HTTP_400"));
        patchMe("{\"nickname\":null}")
                .andExpect(status().isBadRequest());
        patchMe("{\"nickname\":\"   \"}")
                .andExpect(status().isBadRequest());
        patchMe("{\"nickname\":\"" + "가".repeat(51) + "\"}")
                .andExpect(status().isBadRequest());

        User saved = reloadMe();
        assertThat(saved.getNickname()).isEqualTo("기존 닉네임");
        assertThat(saved.getProfileImageUrl()).isEqualTo("https://example.com/old.png");
    }

    @Test
    @DisplayName("수정: 요청에 이메일을 넣어도 무시되고 다른 사용자는 영향받지 않는다")
    void updateMyProfileIgnoresEmailAndKeepsOthers() throws Exception {
        User other = saveUser("uid-other", "other@example.com", "다른 사람", null);

        patchMe("{\"nickname\":\"새 닉네임\",\"email\":\"hacked@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@example.com"));

        assertThat(reloadMe().getEmail()).isEqualTo("me@example.com");
        assertThat(userRepository.findById(other.getId()).orElseThrow().getNickname()).isEqualTo("다른 사람");
    }

    @Test
    @DisplayName("수정: 로그인하지 않으면 401이고 수정되지 않는다")
    void updateMyProfileWithoutLoginIs401() throws Exception {
        mvc.perform(patch(ME)
                        .contentType("application/json")
                        .content("{\"nickname\":\"새 닉네임\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(reloadMe().getNickname()).isEqualTo("기존 닉네임");
    }

    // ---------------------------------------------------------------- 사용자 검색 (GET)

    @Test
    @DisplayName("검색: 이메일이 일치하는 사용자를 id·닉네임·이미지로 찾고 이메일은 응답에 없다")
    void searchUserByEmail() throws Exception {
        User target = saveUser("uid-target", "target@example.com", "찾는 사람", "https://example.com/t.png");
        saveUser("uid-else", "else@example.com", "다른 사람", null);

        search("target@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(target.getId().toString()))
                .andExpect(jsonPath("$.data[0].nickname").value("찾는 사람"))
                .andExpect(jsonPath("$.data[0].profile_image_url").value("https://example.com/t.png"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    @DisplayName("검색: 대소문자와 앞뒤 공백은 무시한다")
    void searchUserIgnoresCaseAndSpaces() throws Exception {
        User target = saveUser("uid-target", "Target@Example.com", "찾는 사람", null);

        search("  TARGET@example.COM  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(target.getId().toString()));
    }

    @Test
    @DisplayName("검색: 일치하는 사용자가 없거나 부분 일치뿐이면 빈 배열이다")
    void searchUserWithoutMatchReturnsEmptyList() throws Exception {
        saveUser("uid-target", "target@example.com", "찾는 사람", null);

        search("none@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
        // 정확히 일치할 때만 찾는다 (부분 일치 불가)
        search("target")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("검색: 내 이메일로 검색하면 본인은 결과에서 제외된다")
    void searchExcludesMyself() throws Exception {
        search("me@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("검색: 이메일이 비어 있거나 공백이면 400")
    void searchWithBlankEmailIs400() throws Exception {
        search("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("HTTP_400"));
        search("   ")
                .andExpect(status().isBadRequest());
        // 파라미터 자체가 없는 경우
        mvc.perform(get(SEARCH).header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검색: 로그인하지 않으면 401")
    void searchWithoutLoginIs401() throws Exception {
        mvc.perform(get(SEARCH).param("email", "target@example.com"))
                .andExpect(status().isUnauthorized());
    }
}
