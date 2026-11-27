package com.travelmaker.backend.preference.controller;

import com.travelmaker.backend.auth.dto.FirebaseIdentity;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.preference.repository.UserPreferenceProfileRepository;
import com.travelmaker.backend.user.domain.User;
import com.travelmaker.backend.user.repository.UserRepository;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 취향 프로파일 조회(GET /users/me/preference-profile) 및 저장/갱신(PUT) API 테스트.
 */
@SpringBootTest
class PreferenceProfileApiTest {

    private static final String ENDPOINT = "/api/v1/users/me/preference-profile";
    private static final String ME_AUTH = "Bearer me-token";
    private static final String OTHER_AUTH = "Bearer other-token";

    @Autowired private WebApplicationContext context;
    @Autowired private UserPreferenceProfileRepository preferenceProfileRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean
    private FirebaseTokenVerifier verifier;

    private MockMvc mvc;
    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        cleanUp();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        me = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-pref-me")
                .email("pref-me@example.com")
                .nickname("나")
                .build());
        other = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-pref-other")
                .email("pref-other@example.com")
                .nickname("다른사용자")
                .build());

        when(verifier.verify("me-token")).thenReturn(
                new FirebaseIdentity("uid-pref-me", "pref-me@example.com", "나", null));
        when(verifier.verify("other-token")).thenReturn(
                new FirebaseIdentity("uid-pref-other", "pref-other@example.com", "다른사용자", null));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        preferenceProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions getProfile(String authHeader) throws Exception {
        return mvc.perform(get(ENDPOINT).header("Authorization", authHeader));
    }

    private ResultActions putProfile(String authHeader, String body) throws Exception {
        return mvc.perform(put(ENDPOINT)
                .header("Authorization", authHeader)
                .contentType("application/json")
                .content(body));
    }

    @Test
    @DisplayName("조회: 프로파일이 아직 없으면 404를 반환한다")
    void getMyProfileWhenMissingReturns404() throws Exception {
        getProfile(ME_AUTH)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HTTP_404"));
    }

    @Test
    @DisplayName("저장 및 조회: 최초 PUT은 version 1로 저장되고 embedding 필드는 없으며 GET으로 동일하게 조회된다")
    void putAndGetMyProfile() throws Exception {
        String body = """
                {
                  "quiz_version": "v1.0",
                  "answers": [
                    { "question_key": "Q_DESTINATION", "selected_option_keys": ["MOUNTAIN", "SEA"] },
                    { "question_key": "Q_FOOD", "selected_option_keys": ["LOCAL_FOOD", "CAFE_TOUR"] }
                  ]
                }
                """;

        putProfile(ME_AUTH, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profile_id").isNotEmpty())
                .andExpect(jsonPath("$.data.model_name").value("rule-based-v1"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.updated_at").exists())
                .andExpect(jsonPath("$.data.has_embedding").doesNotExist())
                .andExpect(jsonPath("$.data.embedding").doesNotExist())
                .andExpect(jsonPath("$.data.profile_data.quiz_version").value("v1.0"))
                .andExpect(jsonPath("$.data.profile_data.answers", hasSize(2)))
                .andExpect(jsonPath("$.data.profile_data.selected_tags",
                        contains("MOUNTAIN", "SEA", "LOCAL_FOOD", "CAFE_TOUR")));

        getProfile(ME_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.model_name").value("rule-based-v1"))
                .andExpect(jsonPath("$.data.profile_data.answers[0].question_key").value("Q_DESTINATION"))
                .andExpect(jsonPath("$.data.profile_data.answers[0].selected_option_keys", contains("MOUNTAIN", "SEA")));
    }

    @Test
    @DisplayName("UPSERT: 동일 사용자가 다시 PUT하면 기존 레코드가 덮어써지고 version이 2로 증가한다")
    void upsertUpdatesExistingRecordAndIncrementsVersion() throws Exception {
        String firstBody = """
                {
                  "quiz_version": "v1.0",
                  "answers": [
                    { "question_key": "Q_DESTINATION", "selected_option_keys": ["MOUNTAIN"] }
                  ]
                }
                """;
        String secondBody = """
                {
                  "quiz_version": "v1.1",
                  "answers": [
                    { "question_key": "Q_DESTINATION", "selected_option_keys": ["CITY"] },
                    { "question_key": "Q_RELAXATION", "selected_option_keys": ["HOT_SPRING_SPA"] }
                  ]
                }
                """;

        putProfile(ME_AUTH, firstBody).andExpect(status().isOk());
        String firstProfileId = preferenceProfileRepository.findByUserId(me.getId()).orElseThrow().getId().toString();

        putProfile(ME_AUTH, secondBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile_id").value(firstProfileId))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.profile_data.quiz_version").value("v1.1"))
                .andExpect(jsonPath("$.data.profile_data.answers", hasSize(2)))
                .andExpect(jsonPath("$.data.profile_data.selected_tags", contains("CITY", "HOT_SPRING_SPA")));

        assertThat(preferenceProfileRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("유연한 키 수용: 신규 키, 문항 건너뛰기([]), 중복 옵션 제거, quiz_version 기본값을 처리한다")
    void flexibleKeysAndNormalization() throws Exception {
        String body = """
                {
                  "answers": [
                    { "question_key": "  Q_CUSTOM_PACE  ", "selected_option_keys": ["SLOW", "SLOW", "SCENIC"] },
                    { "question_key": "Q_SKIPPED", "selected_option_keys": [] },
                    { "question_key": "Q_EXTRA", "selected_option_keys": ["SCENIC", "FOODIE"] }
                  ]
                }
                """;

        putProfile(ME_AUTH, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile_data.quiz_version").value("v1.0"))
                .andExpect(jsonPath("$.data.profile_data.answers[0].question_key").value("Q_CUSTOM_PACE"))
                .andExpect(jsonPath("$.data.profile_data.answers[0].selected_option_keys", contains("SLOW", "SCENIC")))
                .andExpect(jsonPath("$.data.profile_data.answers[1].selected_option_keys", hasSize(0)))
                .andExpect(jsonPath("$.data.profile_data.selected_tags", contains("SLOW", "SCENIC", "FOODIE")));
    }

    @Test
    @DisplayName("사용자 격리: 다른 사용자의 프로파일과 독립적으로 저장·조회된다")
    void profilesAreIsolatedPerUser() throws Exception {
        putProfile(ME_AUTH, """
                {
                  "answers": [{ "question_key": "Q_DESTINATION", "selected_option_keys": ["MOUNTAIN"] }]
                }
                """).andExpect(status().isOk());

        getProfile(OTHER_AUTH).andExpect(status().isNotFound());

        putProfile(OTHER_AUTH, """
                {
                  "answers": [{ "question_key": "Q_DESTINATION", "selected_option_keys": ["SEA"] }]
                }
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        getProfile(ME_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile_data.selected_tags", contains("MOUNTAIN")));
        getProfile(OTHER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile_data.selected_tags", contains("SEA")));
        assertThat(preferenceProfileRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("검증 실패: 잘못된 입력은 모두 400 Bad Request를 반환한다")
    void invalidRequestsReturn400() throws Exception {
        // answers 누락
        putProfile(ME_AUTH, "{\"quiz_version\":\"v1.0\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("HTTP_400"));

        // answers 빈 배열
        putProfile(ME_AUTH, "{\"answers\":[]}")
                .andExpect(status().isBadRequest());

        // question_key 공백
        putProfile(ME_AUTH, """
                {"answers":[{"question_key":"   ","selected_option_keys":["MOUNTAIN"]}]}
                """).andExpect(status().isBadRequest());

        // question_key 중복
        putProfile(ME_AUTH, """
                {
                  "answers":[
                    {"question_key":"Q_DESTINATION","selected_option_keys":["MOUNTAIN"]},
                    {"question_key":"Q_DESTINATION","selected_option_keys":["SEA"]}
                  ]
                }
                """).andExpect(status().isBadRequest());

        // selected_option_keys가 null
        putProfile(ME_AUTH, """
                {"answers":[{"question_key":"Q_DESTINATION","selected_option_keys":null}]}
                """).andExpect(status().isBadRequest());

        // selected_option_keys 항목에 공백 포함
        putProfile(ME_AUTH, """
                {"answers":[{"question_key":"Q_DESTINATION","selected_option_keys":["  "]}]}
                """).andExpect(status().isBadRequest());

        // 전체 선택지 합계 0개
        putProfile(ME_AUTH, """
                {"answers":[{"question_key":"Q_DESTINATION","selected_option_keys":[]}]}
                """).andExpect(status().isBadRequest());

        // quiz_version 공백
        putProfile(ME_AUTH, """
                {
                  "quiz_version":"   ",
                  "answers":[{"question_key":"Q_DESTINATION","selected_option_keys":["MOUNTAIN"]}]
                }
                """).andExpect(status().isBadRequest());

        assertThat(preferenceProfileRepository.count()).isZero();
    }

    @Test
    @DisplayName("인증 실패: 토큰 없이 GET/PUT 요청하면 401 Unauthorized를 반환한다")
    void unauthenticatedRequestsReturn401() throws Exception {
        mvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        mvc.perform(put(ENDPOINT)
                        .contentType("application/json")
                        .content("{\"answers\":[{\"question_key\":\"Q_DESTINATION\",\"selected_option_keys\":[\"SEA\"]}]}"))
                .andExpect(status().isUnauthorized());
    }
}
