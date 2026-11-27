package com.travelmaker.backend.trip.repository;

import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripInvitation;
import com.travelmaker.backend.trip.domain.TripInvitationStatus;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TripInvitationRepositoryTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripInvitationRepository tripInvitationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("회원 ID 기반 및 이메일 기반 초대를 저장하고 PENDING 상태로 조회할 수 있다")
    void saveAndFindPendingInvitations() {
        UUID ownerId = UUID.randomUUID();
        UUID inviteeUserId = UUID.randomUUID();
        String inviteeEmail = "guest@example.com";
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .title("타이베이 여행")
                .destination("타이베이")
                .startDate(LocalDate.of(2026, 12, 20))
                .endDate(LocalDate.of(2026, 12, 24))
                .createdBy(ownerId)
                .build());

        TripInvitation userInvitation = tripInvitationRepository.saveAndFlush(TripInvitation.builder()
                .trip(trip)
                .invitedBy(ownerId)
                .inviteeUserId(inviteeUserId)
                .expiresAt(expiresAt)
                .build());

        TripInvitation emailInvitation = tripInvitationRepository.saveAndFlush(TripInvitation.builder()
                .trip(trip)
                .invitedBy(ownerId)
                .inviteeEmail(inviteeEmail)
                .expiresAt(expiresAt)
                .build());

        assertThat(userInvitation.getRole()).isEqualTo(TripMemberRole.MEMBER);
        assertThat(userInvitation.getStatus()).isEqualTo(TripInvitationStatus.PENDING);
        assertThat(emailInvitation.getCreatedAt()).isNotNull();

        assertThat(tripInvitationRepository.findAllByTripIdAndStatus(trip.getId(), TripInvitationStatus.PENDING))
                .hasSize(2);
        assertThat(tripInvitationRepository.findAllByInviteeUserIdAndStatus(
                inviteeUserId, TripInvitationStatus.PENDING)).hasSize(1);
        assertThat(tripInvitationRepository.findAllByInviteeEmailAndStatus(
                inviteeEmail, TripInvitationStatus.PENDING)).hasSize(1);
        assertThat(tripInvitationRepository.existsByTripIdAndInviteeUserIdAndStatus(
                trip.getId(), inviteeUserId, TripInvitationStatus.PENDING)).isTrue();
        assertThat(tripInvitationRepository.existsByTripIdAndInviteeEmailAndStatus(
                trip.getId(), inviteeEmail, TripInvitationStatus.PENDING)).isTrue();
    }

    private Trip saveTrip(UUID ownerId, String title) {
        return tripRepository.saveAndFlush(Trip.builder()
                .title(title)
                .destination("목적지")
                .startDate(LocalDate.of(2026, 12, 20))
                .endDate(LocalDate.of(2026, 12, 24))
                .createdBy(ownerId)
                .build());
    }

    private TripInvitation saveInvitation(Trip trip, UUID ownerId, UUID inviteeUserId, String inviteeEmail,
                                          TripInvitationStatus status, Instant expiresAt) {
        return tripInvitationRepository.saveAndFlush(TripInvitation.builder()
                .trip(trip)
                .invitedBy(ownerId)
                .inviteeUserId(inviteeUserId)
                .inviteeEmail(inviteeEmail)
                .status(status)
                .expiresAt(expiresAt)
                .build());
    }

    @Test
    @DisplayName("받은 초대: userId 또는 이메일(대소문자 무시)이 맞는 유효한 PENDING 초대만 조회된다")
    void findAllValidByInviteeWithTripFiltersByInviteeStatusAndExpiry() {
        UUID ownerId = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Trip trip = saveTrip(ownerId, "받은 초대 여행");

        TripInvitation byUserId = saveInvitation(trip, ownerId, me, null, TripInvitationStatus.PENDING, future);
        TripInvitation byEmail = saveInvitation(trip, ownerId, null, "ME@Example.com",
                TripInvitationStatus.PENDING, future);
        // 제외 대상
        saveInvitation(trip, ownerId, me, null, TripInvitationStatus.REJECTED, future);
        saveInvitation(trip, ownerId, me, null, TripInvitationStatus.CANCELED, future);
        saveInvitation(trip, ownerId, me, null, TripInvitationStatus.PENDING, past);
        saveInvitation(trip, ownerId, UUID.randomUUID(), null, TripInvitationStatus.PENDING, future);
        saveInvitation(trip, ownerId, null, "other@example.com", TripInvitationStatus.PENDING, future);

        List<TripInvitation> result = tripInvitationRepository.findAllValidByInviteeWithTrip(
                TripInvitationStatus.PENDING, Instant.now(), me, "me@example.com");

        assertThat(result).extracting(TripInvitation::getId)
                .containsExactlyInAnyOrder(byUserId.getId(), byEmail.getId());
    }

    @Test
    @DisplayName("받은 초대: userId와 이메일이 모두 일치하는 초대도 한 번만 조회된다")
    void findAllValidByInviteeWithTripReturnsOnceWhenBothMatch() {
        UUID ownerId = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        Trip trip = saveTrip(ownerId, "중복 일치 여행");
        saveInvitation(trip, ownerId, me, "me@example.com", TripInvitationStatus.PENDING,
                Instant.now().plus(7, ChronoUnit.DAYS));

        assertThat(tripInvitationRepository.findAllValidByInviteeWithTrip(
                TripInvitationStatus.PENDING, Instant.now(), me, "me@example.com")).hasSize(1);
    }

    @Test
    @DisplayName("받은 초대: 최신 초대가 먼저 나오고 여행이 함께 로딩된다")
    void findAllValidByInviteeWithTripOrdersByNewestAndFetchesTrip() throws InterruptedException {
        UUID ownerId = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        Trip older = saveTrip(ownerId, "먼저 받은 초대 여행");
        Trip newer = saveTrip(ownerId, "나중에 받은 초대 여행");

        TripInvitation first = saveInvitation(older, ownerId, me, null, TripInvitationStatus.PENDING, future);
        Thread.sleep(10);
        TripInvitation second = saveInvitation(newer, ownerId, me, null, TripInvitationStatus.PENDING, future);

        entityManager.flush();
        entityManager.clear();

        List<TripInvitation> result = tripInvitationRepository.findAllValidByInviteeWithTrip(
                TripInvitationStatus.PENDING, Instant.now(), me, "me@example.com");

        assertThat(result).extracting(TripInvitation::getId)
                .containsExactly(second.getId(), first.getId());
        // join fetch 이므로 영속성 컨텍스트를 비운 뒤에도 trip이 이미 로딩되어 있다 (N+1 방지)
        assertThat(result).allSatisfy(invitation ->
                assertThat(Hibernate.isInitialized(invitation.getTrip())).isTrue());
        assertThat(result.get(0).getTrip().getTitle()).isEqualTo("나중에 받은 초대 여행");
    }

    @Test
    @DisplayName("여행별 초대 목록: 상태와 상관없이 해당 여행의 초대만 최신순으로 나온다")
    void findAllByTripIdOrderByCreatedAtDesc() throws InterruptedException {
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.now().plus(7, ChronoUnit.DAYS);
        Trip trip = saveTrip(ownerId, "목록 대상 여행");
        Trip other = saveTrip(ownerId, "다른 여행");

        TripInvitation first = saveInvitation(trip, ownerId, UUID.randomUUID(), null,
                TripInvitationStatus.PENDING, future);
        Thread.sleep(10);
        TripInvitation second = saveInvitation(trip, ownerId, null, "a@example.com",
                TripInvitationStatus.ACCEPTED, future);
        Thread.sleep(10);
        TripInvitation third = saveInvitation(trip, ownerId, UUID.randomUUID(), null,
                TripInvitationStatus.CANCELED, future);
        saveInvitation(other, ownerId, UUID.randomUUID(), null, TripInvitationStatus.PENDING, future);

        entityManager.flush();
        entityManager.clear();

        assertThat(tripInvitationRepository.findAllByTripIdOrderByCreatedAtDesc(trip.getId()))
                .extracting(TripInvitation::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(tripInvitationRepository.findAllByTripIdOrderByCreatedAtDesc(UUID.randomUUID())).isEmpty();
    }
}
