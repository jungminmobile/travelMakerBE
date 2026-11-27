package com.travelmaker.backend.trip.controller;

import com.travelmaker.backend.auth.dto.FirebaseIdentity;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.itinerary.repository.ItineraryDayRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryItemRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryRepository;
import com.travelmaker.backend.place.repository.PlaceRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 생성(POST /trips), 내 여행 목록 조회(GET /trips), 여행 수정(PATCH /trips/{tripId}) API 테스트.
 */
@SpringBootTest
class TripApiTest {

    private static final String AUTH_HEADER = "Bearer valid-token";
    private static final String BASE = "/api/v1/trips";

    @Autowired private WebApplicationContext context;
    @Autowired private ItineraryItemRepository itineraryItemRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripMemberRepository tripMemberRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean
    private FirebaseTokenVerifier verifier;

    private MockMvc mvc;
    private User loginUser;

    @BeforeEach
    void setUp() {
        cleanUp();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        loginUser = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-trip")
                .email("trip@example.com")
                .nickname("여행 계획자")
                .build());
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-trip", "trip@example.com", "여행 계획자", null));
    }

    // 다른 테스트 클래스가 같은 DB를 쓰므로 외래키 순서(자식 → 부모)대로 항상 비운다.
    @AfterEach
    void cleanUp() {
        itineraryItemRepository.deleteAll();
        itineraryDayRepository.deleteAll();
        itineraryRepository.deleteAll();
        tripMemberRepository.deleteAll();
        tripRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Trip saveTrip(String title, String destination, LocalDate startDate, LocalDate endDate) {
        return tripRepository.saveAndFlush(Trip.builder()
                .title(title)
                .destination(destination)
                .startDate(startDate)
                .endDate(endDate)
                .createdBy(loginUser.getId())
                .build());
    }

    private void saveMember(Trip trip, UUID userId, TripMemberRole role, TripMemberStatus status) {
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(userId)
                .role(role)
                .status(status)
                .build());
    }

    private ResultActions postTrip(String body) throws Exception {
        return mvc.perform(post(BASE)
                .header("Authorization", AUTH_HEADER)
                .contentType("application/json")
                .content(body));
    }

    private ResultActions getTrips() throws Exception {
        return mvc.perform(get(BASE).header("Authorization", AUTH_HEADER));
    }

    // ---------------------------------------------------------------- 여행 생성 (POST)

    @Test
    @DisplayName("생성: 201과 함께 PLANNING으로 저장되고 만든 사람이 OWNER로 등록된다")
    void createTrip() throws Exception {
        postTrip("{\"title\":\"제주 여행\",\"destination\":\"제주\","
                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trip_id").exists())
                .andExpect(jsonPath("$.data.title").value("제주 여행"))
                .andExpect(jsonPath("$.data.destination").value("제주"))
                .andExpect(jsonPath("$.data.start_date").value("2026-12-01"))
                .andExpect(jsonPath("$.data.end_date").value("2026-12-04"))
                .andExpect(jsonPath("$.data.status").value("PLANNING"));

        List<Trip> trips = tripRepository.findAll();
        assertThat(trips).hasSize(1);
        Trip saved = trips.get(0);
        assertThat(saved.getCreatedBy()).isEqualTo(loginUser.getId());
        assertThat(saved.getBudget()).isNull();
        assertThat(saved.getCurrency()).isEqualTo("KRW");

        List<TripMember> members =
                tripMemberRepository.findAllByTripIdAndStatus(saved.getId(), TripMemberStatus.ACTIVE);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUserId()).isEqualTo(loginUser.getId());
        assertThat(members.get(0).getRole()).isEqualTo(TripMemberRole.OWNER);
    }

    @Test
    @DisplayName("생성: 예산과 통화를 함께 보내면 그대로 저장된다")
    void createTripWithBudgetAndCurrency() throws Exception {
        postTrip("{\"title\":\"도쿄 여행\",\"destination\":\"도쿄\","
                + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-13\","
                + "\"budget\":300000,\"currency\":\"JPY\"}")
                .andExpect(status().isCreated());

        Trip saved = tripRepository.findAll().get(0);
        assertThat(saved.getBudget()).isEqualTo(300000L);
        assertThat(saved.getCurrency()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("생성: 시작일과 종료일이 같은 당일 여행도 만들 수 있다")
    void createSameDayTrip() throws Exception {
        postTrip("{\"title\":\"당일치기\",\"destination\":\"서울\","
                + "\"start_date\":\"2026-12-20\",\"end_date\":\"2026-12-20\"}")
                .andExpect(status().isCreated());

        assertThat(tripRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("생성: 잘못된 값이면 400이고 여행도 멤버도 저장되지 않는다")
    void createTripWithInvalidValuesIs400() throws Exception {
        // 제목 없음
        postTrip("{\"destination\":\"제주\",\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        // 제목이 공백
        postTrip("{\"title\":\"   \",\"destination\":\"제주\","
                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\"}")
                .andExpect(status().isBadRequest());
        // 제목이 100자 초과
        postTrip("{\"title\":\"" + "가".repeat(101) + "\",\"destination\":\"제주\","
                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\"}")
                .andExpect(status().isBadRequest());
        // 목적지가 공백
        postTrip("{\"title\":\"제주 여행\",\"destination\":\" \","
                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\"}")
                .andExpect(status().isBadRequest());
        // 날짜 없음
        postTrip("{\"title\":\"제주 여행\",\"destination\":\"제주\",\"end_date\":\"2026-12-04\"}")
                .andExpect(status().isBadRequest());
        postTrip("{\"title\":\"제주 여행\",\"destination\":\"제주\",\"start_date\":\"2026-12-01\"}")
                .andExpect(status().isBadRequest());
        // 종료일이 시작일보다 빠름
        postTrip("{\"title\":\"제주 여행\",\"destination\":\"제주\","
                + "\"start_date\":\"2026-12-04\",\"end_date\":\"2026-12-01\"}")
                .andExpect(status().isBadRequest());
        // 음수 예산
        postTrip("{\"title\":\"제주 여행\",\"destination\":\"제주\","
                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\",\"budget\":-1}")
                .andExpect(status().isBadRequest());
        // 통화 코드가 3자리가 아님
        postTrip("{\"title\":\"제주 여행\",\"destination\":\"제주\","
                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\",\"currency\":\"KR\"}")
                .andExpect(status().isBadRequest());

        assertThat(tripRepository.count()).isZero();
        assertThat(tripMemberRepository.count()).isZero();
    }

    @Test
    @DisplayName("생성: 로그인하지 않으면 401이고 저장되지 않는다")
    void createTripWithoutLoginIs401() throws Exception {
        mvc.perform(post(BASE)
                        .contentType("application/json")
                        .content("{\"title\":\"제주 여행\",\"destination\":\"제주\","
                                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(tripRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- 내 여행 목록 (GET)

    @Test
    @DisplayName("목록: 내가 ACTIVE인 여행만 시작일 순으로, 내 역할과 ACTIVE 멤버 수와 함께 나온다")
    void getMyTrips() throws Exception {
        UUID other = UUID.randomUUID();
        UUID leftUser = UUID.randomUUID();

        // 내가 OWNER, 혼자
        Trip jeju = saveTrip("제주 여행", "제주", LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 13));
        saveMember(jeju, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        // 내가 MEMBER, 다른 ACTIVE 멤버 1명 + 나간 멤버 1명 (나간 멤버는 세지 않는다)
        Trip busan = saveTrip("부산 여행", "부산", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));
        saveMember(busan, other, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(busan, loginUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);
        saveMember(busan, leftUser, TripMemberRole.MEMBER, TripMemberStatus.LEFT);

        // 내가 나간 여행 → 제외
        Trip left = saveTrip("나간 여행", "강릉", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));
        saveMember(left, other, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(left, loginUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);

        // 내가 속하지 않은 여행 → 제외
        Trip notMine = saveTrip("남의 여행", "여수", LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 8));
        saveMember(notMine, other, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        getTrips()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].trip_id").value(busan.getId().toString()))
                .andExpect(jsonPath("$.data[0].title").value("부산 여행"))
                .andExpect(jsonPath("$.data[0].destination").value("부산"))
                .andExpect(jsonPath("$.data[0].start_date").value("2026-12-01"))
                .andExpect(jsonPath("$.data[0].end_date").value("2026-12-03"))
                .andExpect(jsonPath("$.data[0].status").value("PLANNING"))
                .andExpect(jsonPath("$.data[0].my_role").value("MEMBER"))
                .andExpect(jsonPath("$.data[0].member_count").value(2))
                .andExpect(jsonPath("$.data[1].trip_id").value(jeju.getId().toString()))
                .andExpect(jsonPath("$.data[1].my_role").value("OWNER"))
                .andExpect(jsonPath("$.data[1].member_count").value(1));
    }

    @Test
    @DisplayName("목록: 참여 중인 여행이 없으면 빈 배열이다")
    void getMyTripsWhenEmpty() throws Exception {
        UUID other = UUID.randomUUID();
        Trip notMine = saveTrip("남의 여행", "여수", LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 8));
        saveMember(notMine, other, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        getTrips()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("목록: 방금 만든 여행이 OWNER, 멤버 1명으로 바로 조회된다")
    void createdTripAppearsInList() throws Exception {
        postTrip("{\"title\":\"제주 여행\",\"destination\":\"제주\","
                + "\"start_date\":\"2026-12-01\",\"end_date\":\"2026-12-04\"}")
                .andExpect(status().isCreated());

        getTrips()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("제주 여행"))
                .andExpect(jsonPath("$.data[0].my_role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].member_count").value(1));
    }

    @Test
    @DisplayName("목록: 로그인하지 않으면 401")
    void getMyTripsWithoutLoginIs401() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 여행 수정 (PATCH)

    private ResultActions patchTrip(UUID tripId, String body) throws Exception {
        return mvc.perform(patch(BASE + "/" + tripId)
                .header("Authorization", AUTH_HEADER)
                .contentType("application/json")
                .content(body));
    }

    private Trip reload(Trip trip) {
        return tripRepository.findById(trip.getId()).orElseThrow();
    }

    private Trip saveTripWithBudget(String title, Long budget, String currency) {
        return tripRepository.saveAndFlush(Trip.builder()
                .title(title)
                .destination("제주")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 4))
                .budget(budget)
                .currency(currency)
                .createdBy(loginUser.getId())
                .build());
    }

    private static final String VALID_PATCH = "{\"title\":\"수정된 제목\",\"destination\":\"부산\","
            + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\"}";

    @Test
    @DisplayName("수정: 200과 함께 모든 필드가 바뀌고 상태·생성자는 그대로다")
    void updateTrip() throws Exception {
        Trip trip = saveTripWithBudget("제주 여행", 100000L, "KRW");
        saveMember(trip, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        patchTrip(trip.getId(), "{\"title\":\"수정된 제목\",\"destination\":\"부산\","
                + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\","
                + "\"budget\":250000,\"currency\":\"JPY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.destination").value("부산"))
                .andExpect(jsonPath("$.data.start_date").value("2026-12-10"))
                .andExpect(jsonPath("$.data.end_date").value("2026-12-12"))
                .andExpect(jsonPath("$.data.budget").value(250000))
                .andExpect(jsonPath("$.data.currency").value("JPY"))
                .andExpect(jsonPath("$.data.status").value("PLANNING"));

        Trip saved = reload(trip);
        assertThat(saved.getTitle()).isEqualTo("수정된 제목");
        assertThat(saved.getDestination()).isEqualTo("부산");
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 12));
        assertThat(saved.getBudget()).isEqualTo(250000L);
        assertThat(saved.getCurrency()).isEqualTo("JPY");
        assertThat(saved.getCreatedBy()).isEqualTo(loginUser.getId());
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(trip.getUpdatedAt());
    }

    @Test
    @DisplayName("수정: OWNER가 아닌 ACTIVE 참여자도 수정할 수 있다")
    void updateTripByMember() throws Exception {
        UUID owner = UUID.randomUUID();
        Trip trip = saveTrip("제주 여행", "제주", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4));
        saveMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(trip, loginUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        patchTrip(trip.getId(), VALID_PATCH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));

        assertThat(reload(trip).getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("수정: 통화를 안 보내면 기존 통화가 유지되고, 예산을 안 보내면 예산이 지워진다")
    void updateTripKeepsCurrencyAndClearsBudget() throws Exception {
        Trip trip = saveTripWithBudget("도쿄 여행", 300000L, "JPY");
        saveMember(trip, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        patchTrip(trip.getId(), VALID_PATCH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("JPY"));

        Trip saved = reload(trip);
        assertThat(saved.getCurrency()).isEqualTo("JPY");
        assertThat(saved.getBudget()).isNull();
    }

    @Test
    @DisplayName("수정: 시작일과 종료일이 같은 당일 여행으로도 수정할 수 있다")
    void updateTripToSameDay() throws Exception {
        Trip trip = saveTripWithBudget("제주 여행", null, "KRW");
        saveMember(trip, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        patchTrip(trip.getId(), "{\"title\":\"당일치기\",\"destination\":\"서울\","
                + "\"start_date\":\"2026-12-20\",\"end_date\":\"2026-12-20\"}")
                .andExpect(status().isOk());

        assertThat(reload(trip).getEndDate()).isEqualTo(LocalDate.of(2026, 12, 20));
    }

    @Test
    @DisplayName("수정: 수정한 내용이 내 여행 목록에 바로 반영된다")
    void updatedTripAppearsInList() throws Exception {
        Trip trip = saveTripWithBudget("제주 여행", null, "KRW");
        saveMember(trip, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        patchTrip(trip.getId(), VALID_PATCH).andExpect(status().isOk());

        getTrips()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("수정된 제목"))
                .andExpect(jsonPath("$.data[0].destination").value("부산"))
                .andExpect(jsonPath("$.data[0].start_date").value("2026-12-10"));
    }

    @Test
    @DisplayName("수정: 참여자가 아니거나 나간(LEFT) 사용자, 없는 여행은 모두 403이고 바뀌지 않는다")
    void updateTripByNonParticipantIs403() throws Exception {
        UUID owner = UUID.randomUUID();
        // 내가 속하지 않은 여행
        Trip notMine = saveTrip("남의 여행", "여수", LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 8));
        saveMember(notMine, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        // 내가 나간 여행
        Trip left = saveTrip("나간 여행", "강릉", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));
        saveMember(left, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(left, loginUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);

        patchTrip(notMine.getId(), VALID_PATCH)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HTTP_403"));
        patchTrip(left.getId(), VALID_PATCH)
                .andExpect(status().isForbidden());
        patchTrip(UUID.randomUUID(), VALID_PATCH)
                .andExpect(status().isForbidden());
        // 권한 확인이 검증보다 먼저다: 잘못된 값이어도 참여자가 아니면 400이 아니라 403
        patchTrip(notMine.getId(), "{}")
                .andExpect(status().isForbidden());

        assertThat(reload(notMine).getTitle()).isEqualTo("남의 여행");
        assertThat(reload(left).getTitle()).isEqualTo("나간 여행");
    }

    @Test
    @DisplayName("수정: 잘못된 값이면 400이고 기존 값이 유지된다")
    void updateTripWithInvalidValuesIs400() throws Exception {
        Trip trip = saveTripWithBudget("제주 여행", 100000L, "KRW");
        saveMember(trip, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        // 제목 없음 / 공백 / 100자 초과
        patchTrip(trip.getId(), "{\"destination\":\"부산\",\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HTTP_400"));
        patchTrip(trip.getId(), "{\"title\":\"   \",\"destination\":\"부산\","
                + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\"}")
                .andExpect(status().isBadRequest());
        patchTrip(trip.getId(), "{\"title\":\"" + "가".repeat(101) + "\",\"destination\":\"부산\","
                + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\"}")
                .andExpect(status().isBadRequest());
        // 목적지 공백
        patchTrip(trip.getId(), "{\"title\":\"제목\",\"destination\":\" \","
                + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\"}")
                .andExpect(status().isBadRequest());
        // 날짜 없음
        patchTrip(trip.getId(), "{\"title\":\"제목\",\"destination\":\"부산\",\"end_date\":\"2026-12-12\"}")
                .andExpect(status().isBadRequest());
        patchTrip(trip.getId(), "{\"title\":\"제목\",\"destination\":\"부산\",\"start_date\":\"2026-12-10\"}")
                .andExpect(status().isBadRequest());
        // 종료일이 시작일보다 빠름
        patchTrip(trip.getId(), "{\"title\":\"제목\",\"destination\":\"부산\","
                + "\"start_date\":\"2026-12-12\",\"end_date\":\"2026-12-10\"}")
                .andExpect(status().isBadRequest());
        // 음수 예산
        patchTrip(trip.getId(), "{\"title\":\"제목\",\"destination\":\"부산\","
                + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\",\"budget\":-1}")
                .andExpect(status().isBadRequest());
        // 통화 코드가 3자리가 아님
        patchTrip(trip.getId(), "{\"title\":\"제목\",\"destination\":\"부산\","
                + "\"start_date\":\"2026-12-10\",\"end_date\":\"2026-12-12\",\"currency\":\"KR\"}")
                .andExpect(status().isBadRequest());

        Trip saved = reload(trip);
        assertThat(saved.getTitle()).isEqualTo("제주 여행");
        assertThat(saved.getDestination()).isEqualTo("제주");
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(saved.getBudget()).isEqualTo(100000L);
        assertThat(saved.getCurrency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("수정: 로그인하지 않으면 401이고 바뀌지 않는다")
    void updateTripWithoutLoginIs401() throws Exception {
        Trip trip = saveTripWithBudget("제주 여행", null, "KRW");
        saveMember(trip, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        mvc.perform(patch(BASE + "/" + trip.getId())
                        .contentType("application/json")
                        .content(VALID_PATCH))
                .andExpect(status().isUnauthorized());

        assertThat(reload(trip).getTitle()).isEqualTo("제주 여행");
    }
}
