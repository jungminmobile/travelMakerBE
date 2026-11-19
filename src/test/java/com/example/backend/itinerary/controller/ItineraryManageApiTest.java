package com.example.backend.itinerary.controller;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.auth.security.FirebaseTokenVerifier;
import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.domain.ItineraryDay;
import com.example.backend.itinerary.domain.ItineraryItem;
import com.example.backend.itinerary.domain.ItineraryStatus;
import com.example.backend.itinerary.repository.ItineraryDayRepository;
import com.example.backend.itinerary.repository.ItineraryItemRepository;
import com.example.backend.itinerary.repository.ItineraryRepository;
import com.example.backend.place.domain.Place;
import com.example.backend.place.repository.PlaceRepository;
import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripMember;
import com.example.backend.trip.domain.TripMemberRole;
import com.example.backend.trip.domain.TripMemberStatus;
import com.example.backend.trip.repository.TripMemberRepository;
import com.example.backend.trip.repository.TripRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 상세 조회, 수정, 확정 API 테스트.
 */
@SpringBootTest
class ItineraryManageApiTest {

    private static final String AUTH_HEADER = "Bearer valid-token";
    private static final String BASE = "/api/v1/trips/{tripId}/itineraries";

    @Autowired private WebApplicationContext context;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private ItineraryItemRepository itineraryItemRepository;
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
                .firebaseUid("uid-manage")
                .email("manage@example.com")
                .nickname("일정 관리자")
                .build());
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-manage", "manage@example.com", "일정 관리자", null));
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

    private Trip saveTrip() {
        return tripRepository.saveAndFlush(Trip.builder()
                .title("제주 여행")
                .destination("제주")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 4))
                .createdBy(loginUser.getId())
                .build());
    }

    private void saveMember(Trip trip) {
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(loginUser.getId())
                .role(TripMemberRole.MEMBER)
                .status(TripMemberStatus.ACTIVE)
                .build());
    }

    private Itinerary saveItinerary(Trip trip, int version, ItineraryStatus status) {
        return itineraryRepository.saveAndFlush(Itinerary.builder()
                .tripId(trip.getId())
                .version(version)
                .title("제주 플랜 v" + version)
                .status(status)
                .createdBy(loginUser.getId())
                .build());
    }

    private ItineraryDay saveDay(Itinerary itinerary, int dayNumber) {
        return itineraryDayRepository.saveAndFlush(ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(dayNumber)
                .date(LocalDate.of(2026, 12, dayNumber))
                .title(dayNumber + "일차")
                .build());
    }

    private Place savePlace(String name) {
        return placeRepository.saveAndFlush(Place.builder()
                .name(name)
                .address("제주특별자치도 제주시")
                .latitude(33.394)
                .longitude(126.2397)
                .category("자연/해변")
                .build());
    }

    private void saveItem(ItineraryDay day, Place place, int displayOrder, String title) {
        itineraryItemRepository.saveAndFlush(ItineraryItem.builder()
                .itineraryDay(day)
                .place(place)
                .title(title)
                .displayOrder(displayOrder)
                .startTime(LocalTime.of(10, 30))
                .endTime(LocalTime.of(11, 30))
                .estimatedCost(5000L)
                .recommendationReason("추천 이유")
                .build());
    }

    private Itinerary reload(Itinerary itinerary) {
        return itineraryRepository.findById(itinerary.getId()).orElseThrow();
    }

    // ---------------------------------------------------------------- 상세 조회

    @Test
    @DisplayName("상세 조회는 일차 → 항목 → 장소를 순서대로 중첩해서 반환한다")
    void detailIsNestedAndOrdered() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.ACTIVE);
        ItineraryDay day2 = saveDay(itinerary, 2);   // 일부러 2일차를 먼저 저장
        ItineraryDay day1 = saveDay(itinerary, 1);
        Place place = savePlace("협재해수욕장");
        saveItem(day1, place, 2, "해변 산책");        // 순서 2를 먼저 저장
        saveItem(day1, null, 1, "공항 도착");          // 장소 없는 항목
        saveItem(day2, place, 1, "카페");

        mvc.perform(get(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itinerary_id").value(itinerary.getId().toString()))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.days", hasSize(2)))
                .andExpect(jsonPath("$.data.days[*].day_number", contains(1, 2)))
                .andExpect(jsonPath("$.data.days[0].day_id").value(day1.getId().toString()))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-12-01"))
                .andExpect(jsonPath("$.data.days[0].items", hasSize(2)))
                .andExpect(jsonPath("$.data.days[0].items[*].display_order", contains(1, 2)))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("공항 도착"))
                .andExpect(jsonPath("$.data.days[0].items[0].place").isEmpty())
                .andExpect(jsonPath("$.data.days[0].items[1].place.name").value("협재해수욕장"))
                .andExpect(jsonPath("$.data.days[0].items[1].place.category").value("자연/해변"))
                .andExpect(jsonPath("$.data.days[1].items", hasSize(1)));
    }

    @Test
    @DisplayName("항목의 시간은 HH:mm 형식이고 필드 이름은 명세를 따른다")
    void itemFieldsFollowSpec() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.DRAFT);
        ItineraryDay day = saveDay(itinerary, 1);
        saveItem(day, null, 1, "공항 도착");

        mvc.perform(get(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[0].item_id").exists())
                .andExpect(jsonPath("$.data.days[0].items[0].start_time").value("10:30"))
                .andExpect(jsonPath("$.data.days[0].items[0].end_time").value("11:30"))
                .andExpect(jsonPath("$.data.days[0].items[0].estimated_cost").value(5000))
                .andExpect(jsonPath("$.data.days[0].items[0].recommendation_reason").value("추천 이유"));
    }

    @Test
    @DisplayName("일차가 없는 일정은 days가 빈 배열이다")
    void detailWithoutDays() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.DRAFT);

        mvc.perform(get(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days", hasSize(0)));
    }

    @Test
    @DisplayName("상세 조회: 참여자가 아니면 403")
    void detailNonParticipantIs403() throws Exception {
        Trip trip = saveTrip();
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(get(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상세 조회: 없는 일정이면 404")
    void detailUnknownItineraryIs404() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);

        mvc.perform(get(BASE + "/{itineraryId}", trip.getId(), UUID.randomUUID())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("상세 조회: 다른 여행의 일정 id로는 접근할 수 없다(404)")
    void detailOfAnotherTripIs404() throws Exception {
        Trip trip = saveTrip();
        Trip otherTrip = saveTrip();
        saveMember(trip);
        Itinerary otherItinerary = saveItinerary(otherTrip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(get(BASE + "/{itineraryId}", trip.getId(), otherItinerary.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("상세 조회: 로그인하지 않으면 401")
    void detailWithoutLoginIs401() throws Exception {
        Trip trip = saveTrip();
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(get(BASE + "/{itineraryId}", trip.getId(), itinerary.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 수정

    @Test
    @DisplayName("수정: title과 status를 바꾸면 DB에 반영된다")
    void updateTitleAndStatus() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.DRAFT);

        mvc.perform(patch(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{\"title\":\"새 이름\",\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itinerary_id").value(itinerary.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("새 이름"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        Itinerary saved = reload(itinerary);
        assertThat(saved.getTitle()).isEqualTo("새 이름");
        assertThat(saved.getStatus()).isEqualTo(ItineraryStatus.ARCHIVED);
    }

    @Test
    @DisplayName("수정: title만 보내면 status는 그대로다")
    void updateOnlyTitleKeepsStatus() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(patch(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{\"title\":\"이름만 변경\"}"))
                .andExpect(status().isOk());

        Itinerary saved = reload(itinerary);
        assertThat(saved.getTitle()).isEqualTo("이름만 변경");
        assertThat(saved.getStatus()).isEqualTo(ItineraryStatus.ACTIVE);
    }

    @Test
    @DisplayName("수정: 보낸 값이 없으면 400")
    void updateWithEmptyBodyIs400() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.DRAFT);

        mvc.perform(patch(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("수정: title이 공백이거나 100자를 넘으면 400")
    void updateWithInvalidTitleIs400() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.DRAFT);

        mvc.perform(patch(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{\"title\":\"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(reload(itinerary).getTitle()).isEqualTo("제주 플랜 v1");
    }

    @Test
    @DisplayName("수정: status를 ACTIVE로 바꾸려 하면 400(확정 API를 써야 함)")
    void updateToActiveIs400() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.DRAFT);

        mvc.perform(patch(BASE + "/{itineraryId}", trip.getId(), itinerary.getId())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());

        assertThat(reload(itinerary).getStatus()).isEqualTo(ItineraryStatus.DRAFT);
    }

    @Test
    @DisplayName("수정: 참여자가 아니면 403, 없는 일정이면 404")
    void updateForbiddenAndNotFound() throws Exception {
        Trip otherTrip = saveTrip();
        Itinerary otherItinerary = saveItinerary(otherTrip, 1, ItineraryStatus.DRAFT);

        mvc.perform(patch(BASE + "/{itineraryId}", otherTrip.getId(), otherItinerary.getId())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{\"title\":\"침입\"}"))
                .andExpect(status().isForbidden());

        Trip trip = saveTrip();
        saveMember(trip);
        mvc.perform(patch(BASE + "/{itineraryId}", trip.getId(), UUID.randomUUID())
                        .header("Authorization", AUTH_HEADER)
                        .contentType("application/json")
                        .content("{\"title\":\"없는 일정\"}"))
                .andExpect(status().isNotFound());

        assertThat(reload(otherItinerary).getTitle()).isEqualTo("제주 플랜 v1");
    }

    // ---------------------------------------------------------------- 확정

    @Test
    @DisplayName("확정: 선택한 일정이 ACTIVE가 되고 기존 ACTIVE는 ARCHIVED가 된다")
    void activateArchivesPreviousActive() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary previous = saveItinerary(trip, 1, ItineraryStatus.ACTIVE);
        Itinerary target = saveItinerary(trip, 2, ItineraryStatus.DRAFT);
        Itinerary untouched = saveItinerary(trip, 3, ItineraryStatus.DRAFT);

        mvc.perform(post(BASE + "/{itineraryId}/activate", trip.getId(), target.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activated_itinerary_id").value(target.getId().toString()))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(reload(target).getStatus()).isEqualTo(ItineraryStatus.ACTIVE);
        assertThat(reload(previous).getStatus()).isEqualTo(ItineraryStatus.ARCHIVED);
        assertThat(reload(untouched).getStatus()).isEqualTo(ItineraryStatus.DRAFT);
        assertThat(itineraryRepository.findAllByTripIdAndStatus(trip.getId(), ItineraryStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    @DisplayName("확정: 이미 ACTIVE인 일정을 다시 확정해도 결과는 같다")
    void activateIsIdempotent() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary active = saveItinerary(trip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(post(BASE + "/{itineraryId}/activate", trip.getId(), active.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(reload(active).getStatus()).isEqualTo(ItineraryStatus.ACTIVE);
    }

    @Test
    @DisplayName("확정: 다른 여행의 ACTIVE 일정은 건드리지 않는다")
    void activateDoesNotAffectOtherTrips() throws Exception {
        Trip trip = saveTrip();
        Trip otherTrip = saveTrip();
        saveMember(trip);
        Itinerary target = saveItinerary(trip, 1, ItineraryStatus.DRAFT);
        Itinerary otherActive = saveItinerary(otherTrip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(post(BASE + "/{itineraryId}/activate", trip.getId(), target.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk());

        assertThat(reload(otherActive).getStatus()).isEqualTo(ItineraryStatus.ACTIVE);
    }

    @Test
    @DisplayName("확정: 참여자가 아니면 403이고 상태는 바뀌지 않는다")
    void activateNonParticipantIs403() throws Exception {
        Trip trip = saveTrip();
        Itinerary target = saveItinerary(trip, 1, ItineraryStatus.DRAFT);

        mvc.perform(post(BASE + "/{itineraryId}/activate", trip.getId(), target.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden());

        assertThat(reload(target).getStatus()).isEqualTo(ItineraryStatus.DRAFT);
    }

    @Test
    @DisplayName("확정: 다른 여행의 일정 id면 404")
    void activateOfAnotherTripIs404() throws Exception {
        Trip trip = saveTrip();
        Trip otherTrip = saveTrip();
        saveMember(trip);
        Itinerary otherItinerary = saveItinerary(otherTrip, 1, ItineraryStatus.DRAFT);

        mvc.perform(post(BASE + "/{itineraryId}/activate", trip.getId(), otherItinerary.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());

        assertThat(reload(otherItinerary).getStatus()).isEqualTo(ItineraryStatus.DRAFT);
    }
}
