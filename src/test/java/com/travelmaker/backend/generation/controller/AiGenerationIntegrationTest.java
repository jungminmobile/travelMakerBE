package com.travelmaker.backend.generation.controller;

import com.travelmaker.backend.auth.dto.FirebaseIdentity;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.generation.client.AiServerClient;
import com.travelmaker.backend.generation.client.AiServerException;
import com.travelmaker.backend.generation.client.dto.AiItineraryRequest;
import com.travelmaker.backend.generation.client.dto.AiItineraryResponse;
import com.travelmaker.backend.generation.client.dto.AiItineraryResponse.AiDay;
import com.travelmaker.backend.generation.client.dto.AiItineraryResponse.AiPlace;
import com.travelmaker.backend.generation.client.dto.AiItineraryResponse.AiScheduleItem;
import com.travelmaker.backend.generation.client.dto.AiItineraryResponse.AiSeparateOption;
import com.travelmaker.backend.generation.domain.GenerationJob;
import com.travelmaker.backend.generation.domain.GenerationJobStatus;
import com.travelmaker.backend.generation.repository.GenerationJobRepository;
import com.travelmaker.backend.generation.repository.TripMemberSnapshotRepository;
import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.repository.ItineraryDayRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryItemRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryRepository;
import com.travelmaker.backend.place.domain.Place;
import com.travelmaker.backend.place.repository.PlaceRepository;
import com.travelmaker.backend.preference.domain.UserPreferenceProfile;
import com.travelmaker.backend.preference.repository.UserPreferenceProfileRepository;
import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripMember;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import com.travelmaker.backend.trip.repository.TripMemberRepository;
import com.travelmaker.backend.trip.repository.TripRepository;
import com.travelmaker.backend.user.domain.User;
import com.travelmaker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = "app.ai.generator=ai")
class AiGenerationIntegrationTest {

    private static final String OWNER_AUTH = "Bearer ai-owner-token";

    @Autowired private WebApplicationContext context;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private GenerationJobRepository generationJobRepository;
    @Autowired private TripMemberSnapshotRepository tripMemberSnapshotRepository;
    @Autowired private UserPreferenceProfileRepository userPreferenceProfileRepository;
    @Autowired private ItineraryItemRepository itineraryItemRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private TripMemberRepository tripMemberRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean
    private FirebaseTokenVerifier verifier;

    @MockitoBean
    private AiServerClient aiServerClient;

    private MockMvc mvc;
    private User ownerUser;
    private User memberUser;

    @BeforeEach
    void setUp() {
        cleanUp();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        ownerUser = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-ai-owner")
                .email("ai-owner@example.com")
                .nickname("진수")
                .build());
        memberUser = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-ai-member")
                .email("ai-member@example.com")
                .nickname("민지")
                .build());

        when(verifier.verify("ai-owner-token")).thenReturn(
                new FirebaseIdentity("uid-ai-owner", "ai-owner@example.com", "진수", null));
    }

    @AfterEach
    void cleanUp() {
        itineraryItemRepository.deleteAll();
        itineraryDayRepository.deleteAll();
        itineraryRepository.deleteAll();
        generationJobRepository.deleteAll();
        tripMemberSnapshotRepository.deleteAll();
        userPreferenceProfileRepository.deleteAll();
        tripMemberRepository.deleteAll();
        tripRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Trip saveTokyoTrip() {
        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .title("도쿄 2박 3일")
                .destination("도쿄")
                .startDate(LocalDate.of(2026, 11, 3))
                .endDate(LocalDate.of(2026, 11, 4))
                .budget(800_000L)
                .currency("KRW")
                .createdBy(ownerUser.getId())
                .build());
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(ownerUser.getId())
                .role(TripMemberRole.OWNER)
                .status(TripMemberStatus.ACTIVE)
                .build());
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(memberUser.getId())
                .role(TripMemberRole.MEMBER)
                .status(TripMemberStatus.ACTIVE)
                .build());
        return trip;
    }

    private void savePreference(User user, Map<String, List<String>> answersByQuestion) {
        List<Map<String, Object>> answers = answersByQuestion.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "question_key", entry.getKey(),
                        "selected_option_keys", entry.getValue()
                ))
                .toList();
        userPreferenceProfileRepository.saveAndFlush(UserPreferenceProfile.builder()
                .userId(user.getId())
                .profileData(Map.of(
                        "quiz_version", "v1.0",
                        "answers", answers,
                        "selected_tags", answersByQuestion.values().stream().flatMap(List::stream).toList()
                ))
                .modelName("rule-based-v1")
                .build());
    }

    private UUID extractJobId(MvcResult result) throws Exception {
        JsonNode root = jsonMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(root.path("data").path("job_id").asString());
    }

    private GenerationJob waitForTerminalStatus(UUID jobId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            GenerationJob job = generationJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == GenerationJobStatus.COMPLETED
                    || job.getStatus() == GenerationJobStatus.FAILED) {
                return job;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("비동기 작업이 제한 시간 내에 완료되지 않았습니다: " + jobId);
    }

    @Test
    @DisplayName("AI 일정 생성 요청 시 백엔드 quizSelections를 그대로 전달하고 Place 중복 없이 common/alternate/separate 일정을 저장한다")
    void aiGenerationPersistsPlacesAndModes() throws Exception {
        Trip trip = saveTokyoTrip();
        savePreference(ownerUser, Map.of(
                "food_style", List.of("ramen", "izakaya"),
                "spot_style", List.of("shrine")
        ));
        savePreference(memberUser, Map.of(
                "food_style", List.of("cafe"),
                "spot_style", List.of("shopping")
        ));

        AiPlace sensoji = new AiPlace("sensoji", "센소지", 35.7148, 139.7967, "유적지");
        AiPlace ueno = new AiPlace("ueno_museum", "도쿄국립박물관", 35.7188, 139.7765, "박물관");
        AiPlace shibuya = new AiPlace("shibuya_street", "시부야 스트리트", 35.6595, 139.7004, "쇼핑");

        AiItineraryResponse aiResponse = new AiItineraryResponse(
                "일본",
                "도쿄",
                List.of(
                        new AiDay(1, "2026-11-03", "아사쿠사와 취향 분리 코스", List.of(
                                new AiScheduleItem("10:00", "11:30", "센소지 산책", "common", sensoji,
                                        "아사쿠사 대표 사찰", "공통 유적지 코스", null, null),
                                new AiScheduleItem("14:00", "16:00", "취향별 분리 시간", "separate", null,
                                        "각자 선호 지역 방문", "취향 차이에 따른 분리 일정", null, List.of(
                                        new AiSeparateOption(List.of(ownerUser.getId().toString()), "우에노 박물관",
                                                ueno, "전시 관람", "진수 취향"),
                                        new AiSeparateOption(List.of(memberUser.getId().toString()), "시부야 쇼핑",
                                                shibuya, "쇼핑 투어", "민지 취향")
                                ))
                        )),
                        new AiDay(2, "2026-11-04", "아사쿠사 재방문 코스", List.of(
                                new AiScheduleItem("09:30", "11:00", "센소지 아침 산책", "alternate", sensoji,
                                        "이른 시간 방문", "진수 번갈아 반영",
                                        List.of(ownerUser.getId().toString()), null)
                        ))
                )
        );
        when(aiServerClient.generateItinerary(any())).thenReturn(aiResponse);

        String requestBody = """
                {
                  "destination": "일본",
                  "conditions": {
                    "companion": "친구",
                    "transport": "대중교통",
                    "budget": "50만원-100만원",
                    "tempo": "적당히"
                  },
                  "options": {
                    "max_separate_blocks_per_day": 1
                  }
                }
                """;

        MvcResult postResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = extractJobId(postResult);
        GenerationJob completedJob = waitForTerminalStatus(jobId);
        assertThat(completedJob.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);

        ArgumentCaptor<AiItineraryRequest> requestCaptor = ArgumentCaptor.forClass(AiItineraryRequest.class);
        verify(aiServerClient).generateItinerary(requestCaptor.capture());

        AiItineraryRequest sent = requestCaptor.getValue();
        assertThat(sent.destination()).isEqualTo("일본");
        assertThat(sent.city()).isEqualTo("도쿄");
        assertThat(sent.startDate()).isEqualTo("2026-11-03");
        assertThat(sent.endDate()).isEqualTo("2026-11-04");
        assertThat(sent.conditions().companion()).isEqualTo("친구");
        assertThat(sent.conditions().tempo()).isEqualTo("적당히");
        assertThat(sent.options().maxSeparateBlocksPerDay()).isEqualTo(1);
        assertThat(sent.participants()).hasSize(2);
        assertThat(sent.participants())
                .anySatisfy(p -> {
                    assertThat(p.id()).isEqualTo(ownerUser.getId().toString());
                    assertThat(p.name()).isEqualTo("진수");
                    assertThat(p.quizSelections())
                            .containsEntry("food_style", List.of("ramen", "izakaya"))
                            .containsEntry("spot_style", List.of("shrine"));
                });

        // 동일한 external_place_id("sensoji")가 두 번 나와도 places 테이블에는 1건만 저장되어야 한다.
        List<Place> savedPlaces = placeRepository.findAll();
        assertThat(savedPlaces).hasSize(3);
        assertThat(placeRepository.findByExternalPlaceId("sensoji")).isPresent();

        Itinerary itinerary = itineraryRepository.findByGenerationJobId(jobId).orElseThrow();

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days", hasSize(2)))
                .andExpect(jsonPath("$.data.days[0].items[0].mode").value("common"))
                .andExpect(jsonPath("$.data.days[0].items[0].place.name").value("센소지"))
                .andExpect(jsonPath("$.data.days[0].items[1].mode").value("separate"))
                .andExpect(jsonPath("$.data.days[0].items[1].separate", hasSize(2)))
                .andExpect(jsonPath("$.data.days[0].items[1].separate[0].place.external_place_id").value("ueno_museum"))
                .andExpect(jsonPath("$.data.days[1].items[0].mode").value("alternate"))
                .andExpect(jsonPath("$.data.days[1].items[0].picked_by[0]").value(ownerUser.getId().toString()));
    }

    @Test
    @DisplayName("AI 서버가 retryable 오류를 반환하면 FAILED 상태와 error_code, retryable=true를 노출한다")
    void aiFailureExposesErrorCodeAndRetryable() throws Exception {
        Trip trip = saveTokyoTrip();
        when(aiServerClient.generateItinerary(any()))
                .thenThrow(new AiServerException(503, "LLM_UNAVAILABLE", true, "Gemini 과부하로 일시 실패했습니다."));

        MvcResult postResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = extractJobId(postResult);
        GenerationJob failedJob = waitForTerminalStatus(jobId);

        assertThat(failedJob.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
        assertThat(failedJob.getErrorCode()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(failedJob.getRetryable()).isTrue();

        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), jobId)
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.error_code").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.retryable").value(true))
                .andExpect(jsonPath("$.data.error_message").value("Gemini 과부하로 일시 실패했습니다."));
    }

    @Test
    @DisplayName("AI 서버가 UNSUPPORTED_CITY(400)를 반환하면 retryable=false로 저장한다")
    void unsupportedCityExposesRetryableFalse() throws Exception {
        Trip trip = saveTokyoTrip();
        when(aiServerClient.generateItinerary(any()))
                .thenThrow(new AiServerException(400, "UNSUPPORTED_CITY", false, "지원하지 않는 도시입니다."));

        MvcResult postResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = extractJobId(postResult);
        waitForTerminalStatus(jobId);

        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), jobId)
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.error_code").value("UNSUPPORTED_CITY"))
                .andExpect(jsonPath("$.data.retryable").value(false));
    }
}
