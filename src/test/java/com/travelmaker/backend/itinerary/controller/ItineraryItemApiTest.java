package com.travelmaker.backend.itinerary.controller;

import com.travelmaker.backend.auth.dto.FirebaseIdentity;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.domain.ItineraryDay;
import com.travelmaker.backend.itinerary.domain.ItineraryItem;
import com.travelmaker.backend.itinerary.domain.ItineraryStatus;
import com.travelmaker.backend.itinerary.repository.ItineraryDayRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryItemRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryRepository;
import com.travelmaker.backend.place.domain.Place;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일정 항목 추가(POST), 수정(PATCH), 삭제(DELETE) API 테스트.
 */
@SpringBootTest
class ItineraryItemApiTest {

    private static final String AUTH_HEADER = "Bearer valid-token";
    private static final String ADD_URL = "/api/v1/trips/{tripId}/itineraries/{itineraryId}/days/{dayId}/items";
    private static final String ITEM_URL = "/api/v1/trips/{tripId}/itinerary-items/{itemId}";

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
                .firebaseUid("uid-item")
                .email("item@example.com")
                .nickname("항목 편집자")
                .build());
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-item", "item@example.com", "항목 편집자", null));
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

    private Itinerary saveItinerary(Trip trip, int version) {
        return itineraryRepository.saveAndFlush(Itinerary.builder()
                .tripId(trip.getId())
                .version(version)
                .title("제주 플랜 v" + version)
                .status(ItineraryStatus.DRAFT)
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

    /** 10:30~11:30, 5000엔, 설명 "기존 설명"인 항목을 저장한다. */
    private ItineraryItem saveItem(ItineraryDay day, int displayOrder, String title) {
        return itineraryItemRepository.saveAndFlush(ItineraryItem.builder()
                .itineraryDay(day)
                .title(title)
                .description("기존 설명")
                .displayOrder(displayOrder)
                .startTime(LocalTime.of(10, 30))
                .endTime(LocalTime.of(11, 30))
                .estimatedCost(5000L)
                .build());
    }

    private ItineraryItem reload(ItineraryItem item) {
        return itineraryItemRepository.findById(item.getId()).orElseThrow();
    }

    private ResultActions patchItem(UUID tripId, UUID itemId, String body) throws Exception {
        return mvc.perform(patch(ITEM_URL, tripId, itemId)
                .header("Authorization", AUTH_HEADER)
                .contentType("application/json")
                .content(body));
    }

    private ResultActions postItem(UUID tripId, UUID itineraryId, UUID dayId, String body) throws Exception {
        return mvc.perform(post(ADD_URL, tripId, itineraryId, dayId)
                .header("Authorization", AUTH_HEADER)
                .contentType("application/json")
                .content(body));
    }

    private ResultActions deleteItem(UUID tripId, UUID itemId) throws Exception {
        return mvc.perform(delete(ITEM_URL, tripId, itemId)
                .header("Authorization", AUTH_HEADER));
    }

    // ---------------------------------------------------------------- 항목 수정 (PATCH)

    @Test
    @DisplayName("수정: 시간과 비용을 바꾸면 DB에 반영되고 순서·제목은 그대로다")
    void updateTimesAndCost() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        ItineraryDay day = saveDay(saveItinerary(trip, 1), 1);
        ItineraryItem item = saveItem(day, 3, "공항 도착");

        patchItem(trip.getId(), item.getId(),
                "{\"start_time\":\"14:00\",\"end_time\":\"15:30\",\"estimated_cost\":12000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.item_id").value(item.getId().toString()))
                .andExpect(jsonPath("$.data.updated").value(true));

        ItineraryItem saved = reload(item);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(15, 30));
        assertThat(saved.getEstimatedCost()).isEqualTo(12000L);
        assertThat(saved.getDisplayOrder()).isEqualTo(3);
        assertThat(saved.getTitle()).isEqualTo("공항 도착");
    }

    @Test
    @DisplayName("수정: 일부 값만 보내면 나머지는 기존 값을 유지한다")
    void updateOnlyCostKeepsTimes() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        ItineraryItem item = saveItem(saveDay(saveItinerary(trip, 1), 1), 1, "카페");

        patchItem(trip.getId(), item.getId(), "{\"estimated_cost\":0}")
                .andExpect(status().isOk());

        ItineraryItem saved = reload(item);
        assertThat(saved.getEstimatedCost()).isEqualTo(0L);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(11, 30));
    }

    @Test
    @DisplayName("수정: 잘못된 값이면 400이고 DB는 바뀌지 않는다")
    void updateWithInvalidValuesIs400() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        ItineraryItem item = saveItem(saveDay(saveItinerary(trip, 1), 1), 1, "카페");

        // 보낸 값이 하나도 없음
        patchItem(trip.getId(), item.getId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        // 음수 비용
        patchItem(trip.getId(), item.getId(), "{\"estimated_cost\":-1}")
                .andExpect(status().isBadRequest());
        // 시작이 종료보다 늦음
        patchItem(trip.getId(), item.getId(), "{\"start_time\":\"15:00\",\"end_time\":\"14:00\"}")
                .andExpect(status().isBadRequest());
        // 시작만 보내도 기존 종료(11:30)보다 늦으면 400
        patchItem(trip.getId(), item.getId(), "{\"start_time\":\"12:00\"}")
                .andExpect(status().isBadRequest());
        // 종료만 보내도 기존 시작(10:30)보다 빠르면 400
        patchItem(trip.getId(), item.getId(), "{\"end_time\":\"09:00\"}")
                .andExpect(status().isBadRequest());

        ItineraryItem saved = reload(item);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(saved.getEstimatedCost()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("수정: 참여자가 아니면 403")
    void updateNonParticipantIs403() throws Exception {
        Trip trip = saveTrip();
        ItineraryItem item = saveItem(saveDay(saveItinerary(trip, 1), 1), 1, "카페");

        patchItem(trip.getId(), item.getId(), "{\"estimated_cost\":1}")
                .andExpect(status().isForbidden());

        assertThat(reload(item).getEstimatedCost()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("수정: 없는 항목이면 404")
    void updateUnknownItemIs404() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);

        patchItem(trip.getId(), UUID.randomUUID(), "{\"estimated_cost\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("수정: 다른 여행의 항목 id로는 수정할 수 없다(404)")
    void updateItemOfAnotherTripIs404() throws Exception {
        Trip trip = saveTrip();
        Trip otherTrip = saveTrip();
        saveMember(trip);
        ItineraryItem otherItem = saveItem(saveDay(saveItinerary(otherTrip, 1), 1), 1, "남의 항목");

        patchItem(trip.getId(), otherItem.getId(), "{\"estimated_cost\":1}")
                .andExpect(status().isNotFound());

        assertThat(reload(otherItem).getEstimatedCost()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("수정: 로그인하지 않으면 401")
    void updateWithoutLoginIs401() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        ItineraryItem item = saveItem(saveDay(saveItinerary(trip, 1), 1), 1, "카페");

        mvc.perform(patch(ITEM_URL, trip.getId(), item.getId())
                        .contentType("application/json")
                        .content("{\"estimated_cost\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 항목 추가 (POST)

    @Test
    @DisplayName("추가: 장소를 포함해 추가하면 201과 함께 DB에 저장된다")
    void addItemWithPlace() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1);
        ItineraryDay day = saveDay(itinerary, 1);
        Place place = savePlace("성산일출봉");

        postItem(trip.getId(), itinerary.getId(), day.getId(),
                "{\"place_id\":\"" + place.getId() + "\",\"title\":\"일출 보기\","
                        + "\"description\":\"새벽 등반\",\"start_time\":\"06:00\",\"end_time\":\"08:00\","
                        + "\"display_order\":2,\"estimated_cost\":3000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.item_id").exists())
                .andExpect(jsonPath("$.data.day_id").value(day.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("일출 보기"))
                .andExpect(jsonPath("$.data.display_order").value(2));

        List<ItineraryItem> items = itineraryItemRepository.findAllByItineraryIdWithPlace(itinerary.getId());
        assertThat(items).hasSize(1);
        ItineraryItem saved = items.get(0);
        assertThat(saved.getItineraryDay().getId()).isEqualTo(day.getId());
        assertThat(saved.getPlace().getId()).isEqualTo(place.getId());
        assertThat(saved.getTitle()).isEqualTo("일출 보기");
        assertThat(saved.getDescription()).isEqualTo("새벽 등반");
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getDisplayOrder()).isEqualTo(2);
        assertThat(saved.getEstimatedCost()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("추가: 필수 값만 보내면 장소 없는 항목이 만들어지고 비용은 0이다")
    void addItemWithRequiredFieldsOnly() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1);
        ItineraryDay day = saveDay(itinerary, 1);

        postItem(trip.getId(), itinerary.getId(), day.getId(),
                "{\"title\":\"자유 시간\",\"display_order\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("자유 시간"))
                .andExpect(jsonPath("$.data.display_order").value(1));

        List<ItineraryItem> items = itineraryItemRepository.findAllByItineraryIdWithPlace(itinerary.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPlace()).isNull();
        assertThat(items.get(0).getStartTime()).isNull();
        assertThat(items.get(0).getEstimatedCost()).isEqualTo(0L);
    }

    @Test
    @DisplayName("추가: 존재하지 않는 place_id면 404이고 저장되지 않는다")
    void addItemWithUnknownPlaceIs404() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1);
        ItineraryDay day = saveDay(itinerary, 1);

        postItem(trip.getId(), itinerary.getId(), day.getId(),
                "{\"place_id\":\"" + UUID.randomUUID() + "\",\"title\":\"없는 장소\",\"display_order\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(itineraryItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("추가: 일차가 다른 일정의 것이면 404이고 저장되지 않는다")
    void addItemToDayOfAnotherItineraryIs404() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1);
        Itinerary otherItinerary = saveItinerary(trip, 2);
        ItineraryDay otherDay = saveDay(otherItinerary, 1);

        postItem(trip.getId(), itinerary.getId(), otherDay.getId(),
                "{\"title\":\"엉뚱한 일차\",\"display_order\":1}")
                .andExpect(status().isNotFound());

        assertThat(itineraryItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("추가: 다른 여행의 일정이면 404이고 저장되지 않는다")
    void addItemToItineraryOfAnotherTripIs404() throws Exception {
        Trip trip = saveTrip();
        Trip otherTrip = saveTrip();
        saveMember(trip);
        Itinerary otherItinerary = saveItinerary(otherTrip, 1);
        ItineraryDay otherDay = saveDay(otherItinerary, 1);

        postItem(trip.getId(), otherItinerary.getId(), otherDay.getId(),
                "{\"title\":\"남의 일정\",\"display_order\":1}")
                .andExpect(status().isNotFound());

        assertThat(itineraryItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("추가: 잘못된 값이면 400이고 저장되지 않는다")
    void addItemWithInvalidValuesIs400() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1);
        ItineraryDay day = saveDay(itinerary, 1);

        // 제목이 공백
        postItem(trip.getId(), itinerary.getId(), day.getId(), "{\"title\":\"   \",\"display_order\":1}")
                .andExpect(status().isBadRequest());
        // 제목 없음
        postItem(trip.getId(), itinerary.getId(), day.getId(), "{\"display_order\":1}")
                .andExpect(status().isBadRequest());
        // display_order 없음
        postItem(trip.getId(), itinerary.getId(), day.getId(), "{\"title\":\"순서 없음\"}")
                .andExpect(status().isBadRequest());
        // 음수 비용
        postItem(trip.getId(), itinerary.getId(), day.getId(),
                "{\"title\":\"음수 비용\",\"display_order\":1,\"estimated_cost\":-1}")
                .andExpect(status().isBadRequest());
        // 시작이 종료보다 늦음
        postItem(trip.getId(), itinerary.getId(), day.getId(),
                "{\"title\":\"시간 역전\",\"display_order\":1,\"start_time\":\"15:00\",\"end_time\":\"14:00\"}")
                .andExpect(status().isBadRequest());

        assertThat(itineraryItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("추가: 참여자가 아니면 403이고 저장되지 않는다")
    void addItemNonParticipantIs403() throws Exception {
        Trip trip = saveTrip();
        Itinerary itinerary = saveItinerary(trip, 1);
        ItineraryDay day = saveDay(itinerary, 1);

        postItem(trip.getId(), itinerary.getId(), day.getId(), "{\"title\":\"침입\",\"display_order\":1}")
                .andExpect(status().isForbidden());

        assertThat(itineraryItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("추가: 로그인하지 않으면 401")
    void addItemWithoutLoginIs401() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        Itinerary itinerary = saveItinerary(trip, 1);
        ItineraryDay day = saveDay(itinerary, 1);

        mvc.perform(post(ADD_URL, trip.getId(), itinerary.getId(), day.getId())
                        .contentType("application/json")
                        .content("{\"title\":\"무로그인\",\"display_order\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 항목 삭제 (DELETE)

    @Test
    @DisplayName("삭제: 항목만 지워지고 일차와 다른 항목은 남는다")
    void deleteItem() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        ItineraryDay day = saveDay(saveItinerary(trip, 1), 1);
        ItineraryItem target = saveItem(day, 1, "삭제할 항목");
        ItineraryItem sibling = saveItem(day, 2, "남을 항목");

        deleteItem(trip.getId(), target.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("일정 항목이 삭제되었습니다."));

        assertThat(itineraryItemRepository.existsById(target.getId())).isFalse();
        assertThat(itineraryItemRepository.existsById(sibling.getId())).isTrue();
        assertThat(itineraryDayRepository.existsById(day.getId())).isTrue();
    }

    @Test
    @DisplayName("삭제: 참여자가 아니면 403이고 항목은 남는다")
    void deleteNonParticipantIs403() throws Exception {
        Trip trip = saveTrip();
        ItineraryItem item = saveItem(saveDay(saveItinerary(trip, 1), 1), 1, "항목");

        deleteItem(trip.getId(), item.getId())
                .andExpect(status().isForbidden());

        assertThat(itineraryItemRepository.existsById(item.getId())).isTrue();
    }

    @Test
    @DisplayName("삭제: 없는 항목이면 404")
    void deleteUnknownItemIs404() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);

        deleteItem(trip.getId(), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("삭제: 다른 여행의 항목 id로는 삭제할 수 없다(404)")
    void deleteItemOfAnotherTripIs404() throws Exception {
        Trip trip = saveTrip();
        Trip otherTrip = saveTrip();
        saveMember(trip);
        ItineraryItem otherItem = saveItem(saveDay(saveItinerary(otherTrip, 1), 1), 1, "남의 항목");

        deleteItem(trip.getId(), otherItem.getId())
                .andExpect(status().isNotFound());

        assertThat(itineraryItemRepository.existsById(otherItem.getId())).isTrue();
    }

    @Test
    @DisplayName("삭제: 로그인하지 않으면 401")
    void deleteWithoutLoginIs401() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip);
        ItineraryItem item = saveItem(saveDay(saveItinerary(trip, 1), 1), 1, "항목");

        mvc.perform(delete(ITEM_URL, trip.getId(), item.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(itineraryItemRepository.existsById(item.getId())).isTrue();
    }
}
