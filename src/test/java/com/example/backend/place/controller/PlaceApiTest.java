package com.example.backend.place.controller;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.auth.security.FirebaseTokenVerifier;
import com.example.backend.place.domain.Place;
import com.example.backend.place.repository.PlaceRepository;
import com.example.backend.user.domain.User;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PlaceApiTest {

    private static final String AUTH_HEADER = "Bearer valid-token";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private FirebaseTokenVerifier verifier;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();
        userRepository.deleteAll();

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // 인증 필터가 DB에서 사용자를 찾으므로 로그인한 사용자를 미리 저장해 둔다.
        userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-place")
                .email("place@example.com")
                .nickname("장소 검색자")
                .build());
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-place", "place@example.com", "장소 검색자", null));
    }

    private void savePlace(String externalPlaceId, String name) {
        placeRepository.saveAndFlush(Place.builder()
                .externalPlaceId(externalPlaceId)
                .name(name)
                .address("제주특별자치도 제주시")
                .latitude(33.394)
                .longitude(126.2397)
                .category("관광지")
                .build());
    }

    @Test
    @DisplayName("키워드가 이름에 포함된 장소만 검색된다")
    void searchByKeyword() throws Exception {
        savePlace("id-1", "협재해수욕장");
        savePlace("id-2", "협재 카페");
        savePlace("id-3", "성산일출봉");

        mvc.perform(get("/api/v1/places/search")
                        .param("keyword", "협재")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name",
                        containsInAnyOrder("협재해수욕장", "협재 카페")))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("응답 필드 이름은 API 명세의 스네이크 케이스를 따른다")
    void responseFieldNames() throws Exception {
        savePlace("ChIJ_XYZ", "협재해수욕장");

        mvc.perform(get("/api/v1/places/search")
                        .param("keyword", "협재")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].place_id").exists())
                .andExpect(jsonPath("$.data[0].external_place_id").value("ChIJ_XYZ"))
                .andExpect(jsonPath("$.data[0].name").value("협재해수욕장"))
                .andExpect(jsonPath("$.data[0].address").value("제주특별자치도 제주시"))
                .andExpect(jsonPath("$.data[0].latitude").value(33.394))
                .andExpect(jsonPath("$.data[0].longitude").value(126.2397))
                .andExpect(jsonPath("$.data[0].category").value("관광지"))
                .andExpect(content().string(containsString("\"image_url\"")));
    }

    @Test
    @DisplayName("검색은 대소문자를 구분하지 않는다")
    void searchIgnoresCase() throws Exception {
        savePlace("id-4", "Starbucks Hyeopjae");

        mvc.perform(get("/api/v1/places/search")
                        .param("keyword", "STARBUCKS")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Starbucks Hyeopjae"));
    }

    @Test
    @DisplayName("일치하는 장소가 없으면 빈 배열을 반환한다")
    void noMatchReturnsEmptyList() throws Exception {
        savePlace("id-5", "성산일출봉");

        mvc.perform(get("/api/v1/places/search")
                        .param("keyword", "없는장소")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 반환한다")
    void withoutLoginIs401() throws Exception {
        mvc.perform(get("/api/v1/places/search").param("keyword", "협재"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("keyword가 없으면 400을 반환한다")
    void missingKeywordIs400() throws Exception {
        mvc.perform(get("/api/v1/places/search")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest());
    }
}
