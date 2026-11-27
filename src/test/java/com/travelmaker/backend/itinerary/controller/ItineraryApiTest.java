package com.travelmaker.backend.itinerary.controller;

import com.travelmaker.backend.auth.dto.FirebaseIdentity;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.domain.ItineraryStatus;
import com.travelmaker.backend.itinerary.repository.ItineraryRepository;
import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripMember;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import com.travelmaker.backend.trip.repository.TripMemberRepository;
import com.travelmaker.backend.trip.repository.TripRepository;
import com.travelmaker.backend.user.domain.User;
import com.travelmaker.backend.user.repository.UserRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ItineraryApiTest {

    private static final String AUTH_HEADER = "Bearer valid-token";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private FirebaseTokenVerifier verifier;

    private MockMvc mvc;
    private User loginUser;

    @BeforeEach
    void setUp() {
        // 외래키가 있는 trip_members를 trips보다 먼저 지운다.
        itineraryRepository.deleteAll();
        tripMemberRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        loginUser = userRepository.saveAndFlush(User.builder()
                .firebaseUid("uid-itinerary")
                .email("itinerary@example.com")
                .nickname("일정 조회자")
                .build());
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-itinerary", "itinerary@example.com", "일정 조회자", null));
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

    private void saveMember(Trip trip, TripMemberStatus status) {
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(loginUser.getId())
                .role(TripMemberRole.MEMBER)
                .status(status)
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

    @Test
    @DisplayName("참여자는 여행의 모든 일정 버전을 최신 버전 순으로 조회한다")
    void participantGetsAllVersionsNewestFirst() throws Exception {
        Trip trip = saveTrip();
        Trip otherTrip = saveTrip();
        saveMember(trip, TripMemberStatus.ACTIVE);
        saveItinerary(trip, 1, ItineraryStatus.ARCHIVED);
        saveItinerary(trip, 3, ItineraryStatus.DRAFT);
        saveItinerary(trip, 2, ItineraryStatus.ACTIVE);
        saveItinerary(otherTrip, 1, ItineraryStatus.DRAFT);

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries", trip.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].version", contains(3, 2, 1)))
                .andExpect(jsonPath("$.data[*].status", contains("DRAFT", "ACTIVE", "ARCHIVED")));
    }

    @Test
    @DisplayName("응답 필드 이름은 API 명세를 따른다")
    void responseFieldNames() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip, TripMemberStatus.ACTIVE);
        Itinerary itinerary = saveItinerary(trip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries", trip.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itinerary_id").value(itinerary.getId().toString()))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].title").value("제주 플랜 v1"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].created_at").exists());
    }

    @Test
    @DisplayName("일정이 없으면 빈 배열을 반환한다")
    void noItinerariesReturnsEmptyList() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip, TripMemberStatus.ACTIVE);

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries", trip.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("여행 참여자가 아니면 403을 반환한다")
    void nonParticipantIs403() throws Exception {
        Trip trip = saveTrip();
        saveItinerary(trip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries", trip.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("여행에서 나간 참여자(LEFT)는 403을 반환한다")
    void leftMemberIs403() throws Exception {
        Trip trip = saveTrip();
        saveMember(trip, TripMemberStatus.LEFT);
        saveItinerary(trip, 1, ItineraryStatus.ACTIVE);

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries", trip.getId())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 여행은 참여자가 없으므로 403을 반환한다")
    void unknownTripIs403() throws Exception {
        mvc.perform(get("/api/v1/trips/{tripId}/itineraries", UUID.randomUUID())
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 반환한다")
    void withoutLoginIs401() throws Exception {
        Trip trip = saveTrip();

        mvc.perform(get("/api/v1/trips/{tripId}/itineraries", trip.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
