package com.example.backend.trip.controller;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.auth.security.FirebaseTokenVerifier;
import com.example.backend.itinerary.repository.ItineraryDayRepository;
import com.example.backend.itinerary.repository.ItineraryItemRepository;
import com.example.backend.itinerary.repository.ItineraryRepository;
import com.example.backend.place.repository.PlaceRepository;
import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripInvitation;
import com.example.backend.trip.domain.TripInvitationStatus;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행 나가기, 참여자 강퇴 API 테스트.
 */
@SpringBootTest
class TripMemberApiTest {

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

    private TripMemberStatus statusOf(Trip target, UUID userId) {
        return tripMemberRepository.findByTripIdAndUserId(target.getId(), userId).orElseThrow().getStatus();
    }

    private ResultActions leave(UUID tripId, String authHeader) throws Exception {
        return mvc.perform(delete("/api/v1/trips/" + tripId + "/members/me")
                .header("Authorization", authHeader));
    }

    private ResultActions remove(UUID tripId, UUID targetUserId, String authHeader) throws Exception {
        return mvc.perform(delete("/api/v1/trips/" + tripId + "/members/" + targetUserId)
                .header("Authorization", authHeader));
    }

    // ---------------------------------------------------------------- 여행 나가기

    @Test
    @DisplayName("나가기: 멤버가 나가면 200이고 LEFT로 바뀌며 내 여행 목록에서 사라진다")
    void leaveTrip() throws Exception {
        leave(trip.getId(), MEMBER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.user_id").value(member.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("LEFT"));

        assertThat(statusOf(trip, member.getId())).isEqualTo(TripMemberStatus.LEFT);
        assertThat(statusOf(trip, owner.getId())).isEqualTo(TripMemberStatus.ACTIVE);
        mvc.perform(get("/api/v1/trips").header("Authorization", MEMBER_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("나가기: 나간 뒤에는 여행 하위 API에 접근할 수 없다")
    void leftMemberLosesAccess() throws Exception {
        leave(trip.getId(), MEMBER_AUTH).andExpect(status().isOk());

        mvc.perform(get("/api/v1/trips/" + trip.getId() + "/invitations")
                        .header("Authorization", MEMBER_AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("나가기: 나간 뒤 다시 초대받아 수락하면 같은 멤버 행이 ACTIVE로 복귀한다")
    void rejoinAfterLeave() throws Exception {
        leave(trip.getId(), MEMBER_AUTH).andExpect(status().isOk());
        TripInvitation invitation = tripInvitationRepository.saveAndFlush(TripInvitation.builder()
                .trip(trip)
                .invitedBy(owner.getId())
                .inviteeUserId(member.getId())
                .status(TripInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        mvc.perform(post("/api/v1/invitations/" + invitation.getId() + "/accept")
                        .header("Authorization", MEMBER_AUTH))
                .andExpect(status().isOk());

        assertThat(statusOf(trip, member.getId())).isEqualTo(TripMemberStatus.ACTIVE);
        assertThat(tripMemberRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("나가기: OWNER는 나갈 수 없어 409이고 ACTIVE로 남는다")
    void ownerCannotLeave() throws Exception {
        leave(trip.getId(), OWNER_AUTH)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("방장은 여행을 나갈 수 없습니다."));

        assertThat(statusOf(trip, owner.getId())).isEqualTo(TripMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("나가기: 멤버가 아니거나 이미 나갔거나 없는 여행이면 403")
    void leaveForbidden() throws Exception {
        leave(trip.getId(), STRANGER_AUTH)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("해당 여행의 멤버가 아닙니다."));

        leave(trip.getId(), MEMBER_AUTH).andExpect(status().isOk());
        leave(trip.getId(), MEMBER_AUTH)
                .andExpect(status().isForbidden());

        leave(UUID.randomUUID(), OWNER_AUTH)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("나가기: 로그인하지 않으면 401")
    void leaveWithoutLoginIs401() throws Exception {
        mvc.perform(delete("/api/v1/trips/" + trip.getId() + "/members/me"))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf(trip, member.getId())).isEqualTo(TripMemberStatus.ACTIVE);
    }

    // ---------------------------------------------------------------- 참여자 강퇴

    @Test
    @DisplayName("강퇴: OWNER가 멤버를 내보내면 200이고 LEFT로 바뀌며 접근할 수 없다")
    void removeMember() throws Exception {
        remove(trip.getId(), member.getId(), OWNER_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.user_id").value(member.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("LEFT"));

        assertThat(statusOf(trip, member.getId())).isEqualTo(TripMemberStatus.LEFT);
        assertThat(statusOf(trip, owner.getId())).isEqualTo(TripMemberStatus.ACTIVE);
        mvc.perform(get("/api/v1/trips").header("Authorization", MEMBER_AUTH))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/trips/" + trip.getId() + "/invitations")
                        .header("Authorization", MEMBER_AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("강퇴: OWNER가 아닌 멤버는 403이고 대상은 그대로다")
    void removeByNonOwnerIs403() throws Exception {
        saveMember(trip, stranger.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        remove(trip.getId(), stranger.getId(), MEMBER_AUTH)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("방장만 참여자를 내보낼 수 있습니다."));
        remove(trip.getId(), owner.getId(), MEMBER_AUTH)
                .andExpect(status().isForbidden());

        assertThat(statusOf(trip, stranger.getId())).isEqualTo(TripMemberStatus.ACTIVE);
        assertThat(statusOf(trip, owner.getId())).isEqualTo(TripMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("강퇴: 멤버가 아니거나 나간 사용자, 없는 여행은 403")
    void removeByNonMemberIs403() throws Exception {
        remove(trip.getId(), member.getId(), STRANGER_AUTH)
                .andExpect(status().isForbidden());

        saveMember(trip, stranger.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        remove(trip.getId(), member.getId(), STRANGER_AUTH)
                .andExpect(status().isForbidden());

        remove(UUID.randomUUID(), member.getId(), OWNER_AUTH)
                .andExpect(status().isForbidden());

        assertThat(statusOf(trip, member.getId())).isEqualTo(TripMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("강퇴: OWNER가 자기 자신을 내보내려 하면 409")
    void removeSelfAsOwnerIs409() throws Exception {
        remove(trip.getId(), owner.getId(), OWNER_AUTH)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("방장은 내보낼 수 없습니다."));

        assertThat(statusOf(trip, owner.getId())).isEqualTo(TripMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("강퇴: 여행에 없거나 이미 나간 대상은 404")
    void removeUnknownTargetIs404() throws Exception {
        // 이 여행의 멤버가 아닌 사용자, 존재하지 않는 사용자
        remove(trip.getId(), stranger.getId(), OWNER_AUTH)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("대상 멤버를 찾을 수 없습니다."));
        remove(trip.getId(), UUID.randomUUID(), OWNER_AUTH)
                .andExpect(status().isNotFound());

        // 이미 나간 대상
        leave(trip.getId(), MEMBER_AUTH).andExpect(status().isOk());
        remove(trip.getId(), member.getId(), OWNER_AUTH)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("강퇴: 다른 여행의 멤버는 이 여행 URL로 내보낼 수 없다")
    void removeMemberOfOtherTripIs404() throws Exception {
        Trip other = saveTrip("부산 여행", "부산");
        saveMember(other, stranger.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        remove(trip.getId(), stranger.getId(), OWNER_AUTH)
                .andExpect(status().isNotFound());

        assertThat(statusOf(other, stranger.getId())).isEqualTo(TripMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("강퇴: 로그인하지 않으면 401")
    void removeWithoutLoginIs401() throws Exception {
        mvc.perform(delete("/api/v1/trips/" + trip.getId() + "/members/" + member.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(statusOf(trip, member.getId())).isEqualTo(TripMemberStatus.ACTIVE);
    }
}
