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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 초대 보내기(POST /trips/{tripId}/invitations), 내가 받은 초대 목록(GET /invitations/me) API 테스트.
 */
@SpringBootTest
class InvitationApiTest {

    private static final String AUTH_HEADER = "Bearer valid-token";
    private static final String GUEST_AUTH_HEADER = "Bearer guest-token";
    private static final String GUEST_EMAIL = "guest@example.com";

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
    private User loginUser;
    private User guest;
    private Trip trip;

    @BeforeEach
    void setUp() {
        cleanUp();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        loginUser = saveUser("uid-inviter", "inviter@example.com", "초대하는 사람");
        guest = saveUser("uid-guest", GUEST_EMAIL, "초대받는 사람");
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-inviter", "inviter@example.com", "초대하는 사람", null));
        when(verifier.verify("guest-token")).thenReturn(
                new FirebaseIdentity("uid-guest", GUEST_EMAIL, "초대받는 사람", null));

        // 로그인한 사용자가 OWNER인 여행
        trip = saveTrip("제주 여행", "제주", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4));
        saveMember(trip, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
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

    private Trip saveTrip(String title, String destination, LocalDate startDate, LocalDate endDate) {
        return tripRepository.saveAndFlush(Trip.builder()
                .title(title)
                .destination(destination)
                .startDate(startDate)
                .endDate(endDate)
                .createdBy(loginUser.getId())
                .build());
    }

    private void saveMember(Trip target, UUID userId, TripMemberRole role, TripMemberStatus status) {
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(target)
                .userId(userId)
                .role(role)
                .status(status)
                .build());
    }

    private TripInvitation saveInvitation(Trip target, UUID inviteeUserId, String inviteeEmail,
                                          TripInvitationStatus invitationStatus, Instant expiresAt) {
        return tripInvitationRepository.saveAndFlush(TripInvitation.builder()
                .trip(target)
                .invitedBy(loginUser.getId())
                .inviteeUserId(inviteeUserId)
                .inviteeEmail(inviteeEmail)
                .status(invitationStatus)
                .expiresAt(expiresAt)
                .build());
    }

    private Instant daysFromNow(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private ResultActions postInvitation(UUID tripId, String body) throws Exception {
        return mvc.perform(post("/api/v1/trips/" + tripId + "/invitations")
                .header("Authorization", AUTH_HEADER)
                .contentType("application/json")
                .content(body));
    }

    private ResultActions getMyInvitations(String authHeader) throws Exception {
        return mvc.perform(get("/api/v1/invitations/me").header("Authorization", authHeader));
    }

    private String userIdBody(UUID inviteeUserId) {
        return "{\"invitee_user_id\":\"" + inviteeUserId + "\"}";
    }

    private String emailBody(String email) {
        return "{\"invitee_email\":\"" + email + "\"}";
    }

    // ---------------------------------------------------------------- 초대 보내기 (POST)

    @Test
    @DisplayName("초대: 사용자 id로 초대하면 201이고 PENDING, MEMBER, 7일 뒤 만료로 저장된다")
    void inviteByUserId() throws Exception {
        postInvitation(trip.getId(), userIdBody(guest.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invitation_id").exists())
                .andExpect(jsonPath("$.data.trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data.invitee_user_id").value(guest.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.expires_at").exists());

        List<TripInvitation> saved = tripInvitationRepository.findAll();
        assertThat(saved).hasSize(1);
        TripInvitation invitation = saved.get(0);
        assertThat(invitation.getInvitedBy()).isEqualTo(loginUser.getId());
        assertThat(invitation.getInviteeUserId()).isEqualTo(guest.getId());
        assertThat(invitation.getRole()).isEqualTo(TripMemberRole.MEMBER);
        assertThat(invitation.getStatus()).isEqualTo(TripInvitationStatus.PENDING);
        assertThat(invitation.getExpiresAt())
                .isAfter(daysFromNow(6))
                .isBefore(daysFromNow(8));
        // 초대만 했을 뿐 아직 멤버는 아니다
        assertThat(tripMemberRepository.existsByTripIdAndUserIdAndStatus(
                trip.getId(), guest.getId(), TripMemberStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("초대: 아직 가입하지 않은 이메일로도 초대할 수 있다")
    void inviteByEmailOfUnregisteredUser() throws Exception {
        postInvitation(trip.getId(), emailBody("newbie@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invitee_email").value("newbie@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        TripInvitation invitation = tripInvitationRepository.findAll().get(0);
        assertThat(invitation.getInviteeUserId()).isNull();
        assertThat(invitation.getInviteeEmail()).isEqualTo("newbie@example.com");
    }

    @Test
    @DisplayName("초대: 가입한 사용자를 이메일로 초대하면 그 사용자의 받은 초대 목록에 나타난다")
    void inviteByEmailOfRegisteredUserShowsUpInTheirList() throws Exception {
        postInvitation(trip.getId(), emailBody(GUEST_EMAIL))
                .andExpect(status().isCreated());

        getMyInvitations(GUEST_AUTH_HEADER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data[0].trip_title").value("제주 여행"))
                .andExpect(jsonPath("$.data[0].invited_by").value(loginUser.getId().toString()))
                .andExpect(jsonPath("$.data[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("초대: 여행을 나간(LEFT) 사용자나 이전에 거절(REJECTED)한 사용자는 다시 초대할 수 있다")
    void canReinviteLeftOrRejectedUser() throws Exception {
        saveMember(trip, guest.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        saveInvitation(trip, guest.getId(), null, TripInvitationStatus.REJECTED, daysFromNow(7));

        postInvitation(trip.getId(), userIdBody(guest.getId()))
                .andExpect(status().isCreated());

        assertThat(tripInvitationRepository.findAllByTripIdAndStatus(trip.getId(), TripInvitationStatus.PENDING))
                .hasSize(1);
    }

    @Test
    @DisplayName("초대: 이 여행의 ACTIVE 참여자가 아니면 403이고 저장되지 않는다")
    void inviteByNonParticipantIs403() throws Exception {
        // 내가 속하지 않은 여행
        Trip notMine = saveTrip("남의 여행", "여수", LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 8));
        saveMember(notMine, UUID.randomUUID(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        // 내가 나간 여행
        Trip left = saveTrip("나간 여행", "강릉", LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));
        saveMember(left, UUID.randomUUID(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(left, loginUser.getId(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);

        postInvitation(notMine.getId(), userIdBody(guest.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HTTP_403"));
        postInvitation(left.getId(), userIdBody(guest.getId()))
                .andExpect(status().isForbidden());
        // 존재하지 않는 여행도 참여자가 아니므로 403 (여행 존재 여부를 알려 주지 않는다)
        postInvitation(UUID.randomUUID(), userIdBody(guest.getId()))
                .andExpect(status().isForbidden());

        assertThat(tripInvitationRepository.count()).isZero();
    }

    @Test
    @DisplayName("초대: 초대 대상이 없거나 이메일이 공백이면 400이고 저장되지 않는다")
    void inviteWithInvalidBodyIs400() throws Exception {
        postInvitation(trip.getId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        postInvitation(trip.getId(), emailBody("   "))
                .andExpect(status().isBadRequest());

        assertThat(tripInvitationRepository.count()).isZero();
    }

    @Test
    @DisplayName("초대: 존재하지 않는 사용자 id면 404이고 저장되지 않는다")
    void inviteUnknownUserIdIs404() throws Exception {
        postInvitation(trip.getId(), userIdBody(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HTTP_404"));

        assertThat(tripInvitationRepository.count()).isZero();
    }

    @Test
    @DisplayName("초대: 이미 참여 중인 사용자는 id로도 이메일로도 409이고 저장되지 않는다")
    void inviteExistingMemberIs409() throws Exception {
        saveMember(trip, guest.getId(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        postInvitation(trip.getId(), userIdBody(guest.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HTTP_409"));
        postInvitation(trip.getId(), emailBody(GUEST_EMAIL))
                .andExpect(status().isConflict());

        assertThat(tripInvitationRepository.count()).isZero();
    }

    @Test
    @DisplayName("초대: 이미 PENDING 초대가 있는 사용자 id를 다시 초대하면 409")
    void inviteSameUserIdTwiceIs409() throws Exception {
        postInvitation(trip.getId(), userIdBody(guest.getId())).andExpect(status().isCreated());
        postInvitation(trip.getId(), userIdBody(guest.getId())).andExpect(status().isConflict());

        assertThat(tripInvitationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("초대: 같은 이메일을 다시 초대하면 가입 여부와 상관없이 409")
    void inviteSameEmailTwiceIs409() throws Exception {
        // 미가입 이메일
        postInvitation(trip.getId(), emailBody("newbie@example.com")).andExpect(status().isCreated());
        postInvitation(trip.getId(), emailBody("newbie@example.com")).andExpect(status().isConflict());
        // 가입한 사용자의 이메일 (초대에는 이메일만 저장되므로 이메일 기준 검사가 필요하다)
        postInvitation(trip.getId(), emailBody(GUEST_EMAIL)).andExpect(status().isCreated());
        postInvitation(trip.getId(), emailBody(GUEST_EMAIL)).andExpect(status().isConflict());

        assertThat(tripInvitationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("초대: 로그인하지 않으면 401이고 저장되지 않는다")
    void inviteWithoutLoginIs401() throws Exception {
        mvc.perform(post("/api/v1/trips/" + trip.getId() + "/invitations")
                        .contentType("application/json")
                        .content(userIdBody(guest.getId())))
                .andExpect(status().isUnauthorized());

        assertThat(tripInvitationRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- 받은 초대 목록 (GET)

    @Test
    @DisplayName("목록: 내 id 또는 이메일(대소문자 무시)로 온 유효한 PENDING 초대만 최신순으로 나온다")
    void getMyInvitationsFiltersAndOrders() throws Exception {
        Trip busan = saveTrip("부산 여행", "부산", LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12));
        saveMember(busan, loginUser.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        TripInvitation byId = saveInvitation(trip, guest.getId(), null,
                TripInvitationStatus.PENDING, daysFromNow(7));
        Thread.sleep(10);
        saveInvitation(busan, null, GUEST_EMAIL.toUpperCase(),
                TripInvitationStatus.PENDING, daysFromNow(7));

        // 제외 대상: 거절됨, 만료됨, 다른 사람에게 온 초대
        saveInvitation(trip, guest.getId(), null, TripInvitationStatus.REJECTED, daysFromNow(7));
        saveInvitation(trip, guest.getId(), null, TripInvitationStatus.PENDING, daysFromNow(-1));
        saveInvitation(trip, UUID.randomUUID(), null, TripInvitationStatus.PENDING, daysFromNow(7));
        saveInvitation(trip, null, "other@example.com", TripInvitationStatus.PENDING, daysFromNow(7));

        getMyInvitations(GUEST_AUTH_HEADER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                // 최신 초대(이메일로 받은 부산 여행)가 먼저
                .andExpect(jsonPath("$.data[0].trip_id").value(busan.getId().toString()))
                .andExpect(jsonPath("$.data[0].trip_title").value("부산 여행"))
                .andExpect(jsonPath("$.data[0].destination").value("부산"))
                .andExpect(jsonPath("$.data[0].start_date").value("2026-12-10"))
                .andExpect(jsonPath("$.data[0].end_date").value("2026-12-12"))
                .andExpect(jsonPath("$.data[1].invitation_id").value(byId.getId().toString()))
                .andExpect(jsonPath("$.data[1].trip_id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.data[1].trip_title").value("제주 여행"))
                .andExpect(jsonPath("$.data[1].invited_by").value(loginUser.getId().toString()))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.data[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data[1].expires_at").exists());
    }

    @Test
    @DisplayName("목록: 내가 보낸 초대는 내 받은 목록에 나오지 않고, 받은 초대가 없으면 빈 배열이다")
    void getMyInvitationsWhenNothingReceived() throws Exception {
        saveInvitation(trip, guest.getId(), null, TripInvitationStatus.PENDING, daysFromNow(7));

        // 보낸 사람(loginUser)에게는 받은 초대가 없다
        getMyInvitations(AUTH_HEADER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("목록: 로그인하지 않으면 401")
    void getMyInvitationsWithoutLoginIs401() throws Exception {
        mvc.perform(get("/api/v1/invitations/me"))
                .andExpect(status().isUnauthorized());
    }
}
