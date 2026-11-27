package com.travelmaker.backend.trip.controller;

import com.travelmaker.backend.auth.dto.FirebaseIdentity;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.itinerary.repository.ItineraryDayRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryItemRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryRepository;
import com.travelmaker.backend.place.repository.PlaceRepository;
import com.travelmaker.backend.preference.repository.UserPreferenceProfileRepository;
import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripMember;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import com.travelmaker.backend.trip.domain.TripStatus;
import com.travelmaker.backend.trip.repository.TripInvitationRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 삭제(소프트 삭제) API 테스트.
 */
@SpringBootTest
class TripDeleteApiTest {

    private static final String OWNER_AUTH = "Bearer valid-token";
    private static final String MEMBER_AUTH = "Bearer guest-token";
    private static final String STRANGER_AUTH = "Bearer stranger-token";

    @Autowired private WebApplicationContext context;
    @Autowired private ItineraryItemRepository itineraryItemRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripMemberRepository tripMemberRepository;
    @Autowired private TripInvitationRepository tripInvitationRepository;
    @Autowired private UserPreferenceProfileRepository preferenceProfileRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean
    private FirebaseTokenVerifier verifier;

    private MockMvc mvc;
    private User owner;
    private User member;
    private User stranger;
    private Trip trip;

    @BeforeEach
    void setUp() {
        cleanUp();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        owner = saveUser("uid-owner", "owner@example.com", "방장");
        member = saveUser("uid-member", "member@example.com", "멤버");
        stranger = saveUser("uid-stranger", "stranger@example.com", "외부인");
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-owner", "owner@example.com", "방장", null));
        when(verifier.verify("guest-token")).thenReturn(
                new FirebaseIdentity("uid-member", "member@example.com", "멤버", null));
        when(verifier.verify("stranger-token")).thenReturn(
                new FirebaseIdentity("uid-stranger", "stranger@example.com", "외부인", null));

        // owner가 OWNER, member가 ACTIVE MEMBER인 여행
        trip = saveTrip("제주 여행", "제주");
        saveMember(trip, owner.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(trip, member.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);
    }

    // 다른 테스트 클래스가 같은 DB를 쓰므로 외래키 순서(자식 → 부모)대로 항상 비운다.
    @AfterEach
    void cleanUp() {
        itineraryItemRepository.deleteAll();
        itineraryDayRepository.deleteAll();
        itineraryRepository.deleteAll();
        tripInvitationRepository.deleteAll();
        tripMemberRepository.deleteAll();
        tripRepository.deleteAll();
        placeRepository.deleteAll();
        preferenceProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User saveUser(String firebaseUid, String email, String nickname) {
        return userRepository.saveAndFlush(User.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname(nickname)
                .build());
    }

    private Trip saveTrip(String title, String destination) {
        return tripRepository.saveAndFlush(Trip.builder()
                .title(title)
                .destination(destination)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 4))
                .createdBy(owner.getId())
                .build());
    }

    private void saveMember(Trip target, UUID userId, TripMemberRole role, TripMemberStatus memberStatus) {
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(target)
                .userId(userId)
                .role(role)
                .status(memberStatus)
                .build());
    }

    private TripStatus statusOf(Trip target) {
        return tripRepository.findById(target.getId()).orElseThrow().getStatus();
    }

    private ResultActions deleteTrip(UUID tripId, String authHeader) throws Exception {
        return mvc.perform(delete("/api/v1/trips/" + tripId).header("Authorization", authHeader));
    }

    @Test
    @DisplayName("삭제: OWNER가 삭제하면 200이고 CANCELED로 바뀌며 데이터는 그대로 남는다")
    void deleteTrip() throws Exception {
        deleteTrip(trip.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        // 소프트 삭제: 여행·멤버 행은 그대로 남는다
        assertThat(statusOf(trip)).isEqualTo(TripStatus.CANCELED);
        assertThat(tripMemberRepository.findAll()).hasSize(2);
        assertThat(tripRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("삭제: 삭제된 여행은 모든 멤버의 내 여행 목록에서 사라진다")
    void deletedTripDisappearsFromList() throws Exception {
        deleteTrip(trip.getId(), OWNER_AUTH).andExpect(status().isOk());

        mvc.perform(get("/api/v1/trips").header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/trips").header("Authorization", MEMBER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("삭제: 삭제한 여행만 목록에서 빠지고 다른 여행은 남는다")
    void otherTripsRemainInList() throws Exception {
        Trip other = saveTrip("부산 여행", "부산");
        saveMember(other, owner.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        deleteTrip(trip.getId(), OWNER_AUTH).andExpect(status().isOk());

        mvc.perform(get("/api/v1/trips").header("Authorization", OWNER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].trip_id").value(other.getId().toString()));
        assertThat(statusOf(other)).isEqualTo(TripStatus.PLANNING);
    }

    @Test
    @DisplayName("삭제: 삭제된 여행의 상세 조회는 404")
    void deletedTripDetailIs404() throws Exception {
        deleteTrip(trip.getId(), OWNER_AUTH).andExpect(status().isOk());

        mvc.perform(get("/api/v1/trips/" + trip.getId()).header("Authorization", OWNER_AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/v1/trips/" + trip.getId()).header("Authorization", MEMBER_AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("삭제: 이미 삭제된 여행을 다시 삭제하면 404")
    void deleteTwiceIs404() throws Exception {
        deleteTrip(trip.getId(), OWNER_AUTH).andExpect(status().isOk());

        deleteTrip(trip.getId(), OWNER_AUTH)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("일치하는 여행이 없습니다."));
        assertThat(statusOf(trip)).isEqualTo(TripStatus.CANCELED);
    }

    @Test
    @DisplayName("삭제: OWNER가 아닌 멤버는 403이고 여행은 그대로다")
    void deleteByNonOwnerIs403() throws Exception {
        deleteTrip(trip.getId(), MEMBER_AUTH)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("여행 삭제는 소유자만이 가능합니다."));

        assertThat(statusOf(trip)).isEqualTo(TripStatus.PLANNING);
    }

    @Test
    @DisplayName("삭제: 멤버가 아니거나 나간 멤버, 없는 여행은 403")
    void deleteByNonMemberIs403() throws Exception {
        deleteTrip(trip.getId(), STRANGER_AUTH)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("해당 여행의 멤버가 아닙니다."));

        saveMember(trip, stranger.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        deleteTrip(trip.getId(), STRANGER_AUTH)
                .andExpect(status().isForbidden());

        deleteTrip(UUID.randomUUID(), OWNER_AUTH)
                .andExpect(status().isForbidden());

        assertThat(statusOf(trip)).isEqualTo(TripStatus.PLANNING);
    }

    @Test
    @DisplayName("삭제: 로그인하지 않으면 401")
    void deleteWithoutLoginIs401() throws Exception {
        mvc.perform(delete("/api/v1/trips/" + trip.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf(trip)).isEqualTo(TripStatus.PLANNING);
    }
}
