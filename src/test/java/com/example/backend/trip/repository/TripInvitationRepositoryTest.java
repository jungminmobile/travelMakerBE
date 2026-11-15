package com.example.backend.trip.repository;

import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripInvitation;
import com.example.backend.trip.domain.TripInvitationStatus;
import com.example.backend.trip.domain.TripMemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TripInvitationRepositoryTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripInvitationRepository tripInvitationRepository;

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
}
