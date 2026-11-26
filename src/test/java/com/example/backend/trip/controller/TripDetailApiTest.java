package com.example.backend.trip.controller;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.auth.security.FirebaseTokenVerifier;
import com.example.backend.itinerary.repository.ItineraryDayRepository;
import com.example.backend.itinerary.repository.ItineraryItemRepository;
import com.example.backend.itinerary.repository.ItineraryRepository;
import com.example.backend.place.repository.PlaceRepository;
import com.example.backend.preference.domain.UserPreferenceProfile;
import com.example.backend.preference.repository.UserPreferenceProfileRepository;
import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripMember;
import com.example.backend.trip.domain.TripMemberRole;
import com.example.backend.trip.domain.TripMemberStatus;
import com.example.backend.trip.repository.TripInvitationRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 상세 및 멤버 목록 조회 API 테스트.
 */
@SpringBootTest
class TripDetailApiTest {

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
    void setUp() throws InterruptedException {
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

        // owner가 먼저, member가 나중에 가입한 여행 (joinedAt 순서를 구분하기 위해 간격을 둔다)
        trip = saveTrip("제주 가을 힐링", "제주", 1_200_000L, "KRW");
        saveMember(trip, owner.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        Thread.sleep(10);
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

    private Trip saveTrip(String title, String destination, Long budget, String currency) {
        return tripRepository.saveAndFlush(Trip.builder()
                .title(title)
                .destination(destination)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 4))
                .budget(budget)
                .currency(currency)
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

    private void savePreference(User user) {
        preferenceProfileRepository.saveAndFlush(UserPreferenceProfile.builder()
                .userId(user.getId())
                .profileData(Map.of("pace", "relaxed"))
                .modelName("test-model")
                .build());
    }

    private ResultActions getDetail(UUID tripId, String authHeader) throws Exception {
        return mvc.perform(get("/api/v1/trips/" + tripId).header("Authorization", authHeader));
    }

    @Test
    @DisplayName("상세: 여행 정보와 멤버 목록이 가입 순으로 나오고 has_preference가 멤버별로 채워진다")
    void getTripDetail() throws Exception {
        savePreference(member); // 멤버만 취향 프로필이 있다

        getDetail(trip.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("제주 가을 힐링"))
                .andExpect(jsonPath("$.data.destination").value("제주"))
                .andExpect(jsonPath("$.data.start_date").value("2026-12-01"))
                .andExpect(jsonPath("$.data.end_date").value("2026-12-04"))
                .andExpect(jsonPath("$.data.budget").value(1_200_000))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.status").value("PLANNING"))
                .andExpect(jsonPath("$.data.members", hasSize(2)))
                // 먼저 가입한 방장이 앞
                .andExpect(jsonPath("$.data.members[0].user_id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.data.members[0].nickname").value("방장"))
                .andExpect(jsonPath("$.data.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data.members[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.members[0].has_preference").value(false))
                .andExpect(jsonPath("$.data.members[0].joined_at").exists())
                .andExpect(jsonPath("$.data.members[0].profile_image_url").doesNotExist())
                .andExpect(jsonPath("$.data.members[1].user_id").value(member.getId().toString()))
                .andExpect(jsonPath("$.data.members[1].nickname").value("멤버"))
                .andExpect(jsonPath("$.data.members[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.members[1].has_preference").value(true));
    }

    @Test
    @DisplayName("상세: 모든 멤버가 취향 프로필을 만들었으면 has_preference가 모두 true")
    void allMembersHavePreference() throws Exception {
        savePreference(owner);
        savePreference(member);

        getDetail(trip.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].has_preference").value(true))
                .andExpect(jsonPath("$.data.members[1].has_preference").value(true));
    }

    @Test
    @DisplayName("상세: 멤버 목록은 가입 순이다 (나중에 가입한 멤버가 뒤)")
    void membersAreOrderedByJoinTime() throws Exception {
        Thread.sleep(10);
        saveMember(trip, stranger.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        getDetail(trip.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members", hasSize(3)))
                .andExpect(jsonPath("$.data.members[0].user_id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.data.members[1].user_id").value(member.getId().toString()))
                .andExpect(jsonPath("$.data.members[2].user_id").value(stranger.getId().toString()));
    }

    @Test
    @DisplayName("상세: OWNER가 아닌 멤버도 조회할 수 있다")
    void memberCanGetDetail() throws Exception {
        getDetail(trip.getId(), MEMBER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.members", hasSize(2)));
    }

    @Test
    @DisplayName("상세: 나간 멤버는 목록에 나오지 않는다")
    void leftMembersAreExcluded() throws Exception {
        saveMember(trip, stranger.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);

        getDetail(trip.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members", hasSize(2)));
    }

    @Test
    @DisplayName("상세: 다른 여행의 멤버는 섞이지 않는다")
    void otherTripMembersAreNotMixed() throws Exception {
        Trip other = saveTrip("부산 여행", "부산", null, null);
        saveMember(other, stranger.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        getDetail(trip.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members", hasSize(2)));
        getDetail(other.getId(), STRANGER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("부산 여행"))
                .andExpect(jsonPath("$.data.members", hasSize(1)))
                .andExpect(jsonPath("$.data.members[0].user_id").value(stranger.getId().toString()));
    }

    @Test
    @DisplayName("상세: 예산이 없는 여행도 조회된다")
    void tripWithoutBudget() throws Exception {
        Trip noBudget = saveTrip("당일치기", "서울", null, null);
        saveMember(noBudget, owner.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        getDetail(noBudget.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("당일치기"))
                .andExpect(jsonPath("$.data.budget").doesNotExist());
    }

    @Test
    @DisplayName("상세: 멤버가 아니거나 나간 멤버, 없는 여행은 403")
    void forbidden() throws Exception {
        getDetail(trip.getId(), STRANGER_AUTH)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("해당 여행의 멤버가 아닙니다."));

        saveMember(trip, stranger.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        getDetail(trip.getId(), STRANGER_AUTH)
                .andExpect(status().isForbidden());

        getDetail(UUID.randomUUID(), OWNER_AUTH)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상세: 로그인하지 않으면 401")
    void withoutLoginIs401() throws Exception {
        mvc.perform(get("/api/v1/trips/" + trip.getId()))
                .andExpect(status().isUnauthorized());
    }
}
