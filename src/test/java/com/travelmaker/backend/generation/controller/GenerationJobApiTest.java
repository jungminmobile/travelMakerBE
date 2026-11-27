package com.travelmaker.backend.generation.controller;

import com.travelmaker.backend.auth.dto.FirebaseIdentity;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.generation.domain.GenerationJob;
import com.travelmaker.backend.generation.domain.GenerationJobStatus;
import com.travelmaker.backend.generation.domain.TripMemberSnapshot;
import com.travelmaker.backend.generation.generator.ItineraryGenerator;
import com.travelmaker.backend.generation.repository.GenerationJobRepository;
import com.travelmaker.backend.generation.repository.TripMemberSnapshotRepository;
import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.domain.ItineraryStatus;
import com.travelmaker.backend.itinerary.repository.ItineraryDayRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryItemRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryRepository;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class GenerationJobApiTest {

    private static final String OWNER_AUTH = "Bearer owner-token";
    private static final String MEMBER_AUTH = "Bearer member-token";

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

    @MockitoSpyBean
    private ItineraryGenerator itineraryGenerator;

    private MockMvc mvc;
    private User ownerUser;
    private User memberUser;

    @BeforeEach
    void setUp() {
        cleanUp();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        ownerUser = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-gen-owner")
                .email("gen-owner@example.com")
                .nickname("방장")
                .build());
        memberUser = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-gen-member")
                .email("gen-member@example.com")
                .nickname("참여자")
                .build());

        when(verifier.verify("owner-token")).thenReturn(
                new FirebaseIdentity("uid-gen-owner", "gen-owner@example.com", "방장", null));
        when(verifier.verify("member-token")).thenReturn(
                new FirebaseIdentity("uid-gen-member", "gen-member@example.com", "참여자", null));
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(itineraryGenerator);
        cleanUp();
    }

    private void cleanUp() {
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

    private Trip saveTrip(LocalDate startDate, LocalDate endDate) {
        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .title("제주 가을 힐링 여행")
                .destination("제주")
                .startDate(startDate)
                .endDate(endDate)
                .budget(1200000L)
                .currency("KRW")
                .createdBy(ownerUser.getId())
                .build());
        saveMember(trip, ownerUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        return trip;
    }

    private void saveMember(Trip trip, UUID userId, TripMemberRole role, TripMemberStatus status) {
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(userId)
                .role(role)
                .status(status)
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
    @DisplayName("생성 요청(202) 후 비동기로 Stub 일정이 DB에 저장되고 기존 일정 상세 조회 API로 조회된다")
    void createJobCompletesAndPersistsItinerary() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));

        MvcResult postResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH)
                        .contentType("application/json")
                        .content("{\"special_requests\":\"부모님과 걷기 쉬운 코스\",\"include_budget_optimization\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.job_id").exists())
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.estimated_seconds").value(15))
                .andExpect(jsonPath("$.data.created_at").exists())
                .andReturn();

        UUID jobId = extractJobId(postResult);
        GenerationJob completedJob = waitForTerminalStatus(jobId);

        assertThat(completedJob.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
        assertThat(completedJob.getProgressPercent()).isEqualTo(100);
        assertThat(completedJob.getInputData())
                .containsEntry("special_requests", "부모님과 걷기 쉬운 코스")
                .containsEntry("include_budget_optimization", true);
        assertThat(completedJob.getResultData())
                .containsEntry("version", 1)
                .containsEntry("day_count", 3)
                .containsEntry("item_count", 9);

        Itinerary generated = itineraryRepository.findByGenerationJobId(jobId).orElseThrow();
        assertThat(generated.getVersion()).isEqualTo(1);
        assertThat(generated.getStatus()).isEqualTo(ItineraryStatus.DRAFT);

        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), jobId)
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.job_id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.progress_percent").value(100))
                .andExpect(jsonPath("$.data.itinerary_id").value(generated.getId().toString()))
                .andExpect(jsonPath("$.data.completed_at").exists())
                .andExpect(jsonPath("$.data.error_message").doesNotExist());

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries/{itineraryId}", trip.getId(), generated.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itinerary_id").value(generated.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.days", hasSize(3)))
                .andExpect(jsonPath("$.data.days[0].day_number").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-12-01"))
                .andExpect(jsonPath("$.data.days[0].items", hasSize(3)));
    }

    @Test
    @DisplayName("MEMBER 참여자도 빈 바디로 요청할 수 있고, 두 번째 생성은 version 2가 된다")
    void memberCanGenerateAndVersionIncrements() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));
        saveMember(trip, memberUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        MvcResult firstResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", MEMBER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID firstJobId = extractJobId(firstResult);
        waitForTerminalStatus(firstJobId);

        MvcResult secondResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID secondJobId = extractJobId(secondResult);
        GenerationJob secondCompleted = waitForTerminalStatus(secondJobId);

        assertThat(secondCompleted.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
        Itinerary secondItinerary = itineraryRepository.findByGenerationJobId(secondJobId).orElseThrow();
        assertThat(secondItinerary.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("생성 중 예외가 발생하면 FAILED로 전이되고 일정은 저장되지 않는다")
    void generatorExceptionMarksJobFailed() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
        doThrow(new IllegalStateException("선택한 일정 기간 내 운영되는 장소 풀이 부족합니다."))
                .when(itineraryGenerator)
                .generate(any());

        MvcResult postResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = extractJobId(postResult);
        GenerationJob failedJob = waitForTerminalStatus(jobId);

        assertThat(failedJob.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
        assertThat(itineraryRepository.findByGenerationJobId(jobId)).isEmpty();

        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), jobId)
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.error_message").value("선택한 일정 기간 내 운영되는 장소 풀이 부족합니다."))
                .andExpect(jsonPath("$.data.completed_at").exists())
                .andExpect(jsonPath("$.data.itinerary_id").doesNotExist());
    }

    @Test
    @DisplayName("Polling 조회 시 PENDING 및 RUNNING 상태별 필드를 명세대로 반환한다")
    void pollingReturnsPendingAndRunningShapes() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));

        GenerationJob pendingJob = generationJobRepository.saveAndFlush(GenerationJob.builder()
                .tripId(trip.getId())
                .requestedBy(ownerUser.getId())
                .inputData(Map.of("trip_id", trip.getId().toString()))
                .build());

        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), pendingJob.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.progress_percent").value(0))
                .andExpect(jsonPath("$.data.created_at").exists())
                .andExpect(jsonPath("$.data.started_at").doesNotExist());

        pendingJob.start(Instant.now());
        pendingJob.updateProgress(60);
        generationJobRepository.saveAndFlush(pendingJob);

        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), pendingJob.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.progress_percent").value(60))
                .andExpect(jsonPath("$.data.started_at").exists())
                .andExpect(jsonPath("$.data.itinerary_id").doesNotExist());
    }

    @Test
    @DisplayName("작업 이력 조회는 해당 여행의 작업만 최신순으로 반환한다")
    void getJobsReturnsTripHistoryNewestFirst() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));
        Trip otherTrip = saveTrip(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));

        MvcResult firstResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID firstJobId = extractJobId(firstResult);
        waitForTerminalStatus(firstJobId);

        Thread.sleep(5);

        MvcResult secondResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();
        UUID secondJobId = extractJobId(secondResult);
        waitForTerminalStatus(secondJobId);

        generationJobRepository.saveAndFlush(GenerationJob.builder()
                .tripId(otherTrip.getId())
                .requestedBy(ownerUser.getId())
                .inputData(Map.of("trip_id", otherTrip.getId().toString()))
                .build());

        Itinerary secondItinerary = itineraryRepository.findByGenerationJobId(secondJobId).orElseThrow();

        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].job_id").value(secondJobId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].progress_percent").value(100))
                .andExpect(jsonPath("$.data[0].itinerary_id").value(secondItinerary.getId().toString()))
                .andExpect(jsonPath("$.data[1].job_id").value(firstJobId.toString()));
    }

    @Test
    @DisplayName("진행 중(PENDING 또는 RUNNING)인 작업이 있으면 409 Conflict를 반환한다")
    void activeJobConflictIs409() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
        GenerationJob existing = generationJobRepository.saveAndFlush(GenerationJob.builder()
                .tripId(trip.getId())
                .requestedBy(ownerUser.getId())
                .inputData(Map.of("trip_id", trip.getId().toString()))
                .build());

        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        existing.start(Instant.now());
        generationJobRepository.saveAndFlush(existing);

        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("special_requests가 1000자를 초과하면 400 Bad Request를 반환한다")
    void tooLongSpecialRequestsIs400() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
        String tooLong = "가".repeat(1001);

        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH)
                        .contentType("application/json")
                        .content("{\"special_requests\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("권한·존재 여부 검증: 미인증 401, 비참여자/LEFT 403, 없는 여행/작업 404")
    void authorizationAndNotFoundCases() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
        Trip otherTrip = saveTrip(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 11));
        GenerationJob otherJob = generationJobRepository.saveAndFlush(GenerationJob.builder()
                .tripId(otherTrip.getId())
                .requestedBy(ownerUser.getId())
                .inputData(Map.of("trip_id", otherTrip.getId().toString()))
                .build());

        // 미인증 401
        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId()))
                .andExpect(status().isUnauthorized());

        // 비참여자 403
        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", MEMBER_AUTH))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", MEMBER_AUTH))
                .andExpect(status().isForbidden());

        // LEFT 참여자 403
        saveMember(trip, memberUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", MEMBER_AUTH))
                .andExpect(status().isForbidden());

        // 없는 여행 404
        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", UUID.randomUUID())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isNotFound());

        // 없는 jobId 404
        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), UUID.randomUUID())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isNotFound());

        // 다른 여행에 속한 jobId 404
        mvc.perform(get("/api/v1/trips/{tripId}/generation-jobs/{jobId}", trip.getId(), otherJob.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("생성 요청 시 참여자 취향 프로파일이 백엔드 퀴즈 포맷으로 스냅샷 복제되며 이후 프로파일 변경에 영향받지 않는다")
    void capturesImmutablePreferenceSnapshotsWithBackendQuizKeys() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
        saveMember(trip, memberUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        UserPreferenceProfile ownerProfile = userPreferenceProfileRepository.saveAndFlush(UserPreferenceProfile.builder()
                .userId(ownerUser.getId())
                .profileData(Map.of(
                        "quiz_version", "v1.0",
                        "answers", List.of(Map.of(
                                "question_key", "q_food",
                                "selected_option_keys", List.of("ramen", "sushi")
                        )),
                        "selected_tags", List.of("ramen", "sushi")
                ))
                .modelName("rule-based-v1")
                .build());

        MvcResult postResult = mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID jobId = extractJobId(postResult);
        waitForTerminalStatus(jobId);

        // 이후 원 프로파일을 수정해도 이미 복제된 스냅샷과 job.input_data는 유지되어야 한다.
        ownerProfile.update(Map.of(
                "quiz_version", "v2.0",
                "answers", List.of(Map.of(
                        "question_key", "q_food",
                        "selected_option_keys", List.of("bakery")
                )),
                "selected_tags", List.of("bakery")
        ), "rule-based-v1");
        userPreferenceProfileRepository.saveAndFlush(ownerProfile);

        List<TripMemberSnapshot> snapshots = tripMemberSnapshotRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());
        assertThat(snapshots).hasSize(2);
        TripMemberSnapshot ownerSnapshot = snapshots.stream()
                .filter(s -> s.getUserId().equals(ownerUser.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(ownerSnapshot.getSourceProfileVersion()).isEqualTo(1);
        assertThat(ownerSnapshot.getProfileSnapshot())
                .containsEntry("quiz_version", "v1.0")
                .containsEntry("has_preference", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> quizSelections =
                (Map<String, Object>) ownerSnapshot.getProfileSnapshot().get("quiz_selections");
        assertThat(quizSelections).containsEntry("q_food", List.of("ramen", "sushi"));
    }

    @Test
    @DisplayName("options.max_separate_blocks_per_day가 범위를 벗어나면 400 Bad Request를 반환한다")
    void invalidMaxSeparateBlocksIs400() throws Exception {
        Trip trip = saveTrip(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));

        mvc.perform(post("/api/v1/trips/{tripId}/generation-jobs", trip.getId())
                        .header("Authorization", OWNER_AUTH)
                        .contentType("application/json")
                        .content("{\"options\":{\"max_separate_blocks_per_day\":-1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
