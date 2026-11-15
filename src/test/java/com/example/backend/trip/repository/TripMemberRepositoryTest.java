package com.example.backend.trip.repository;

import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripMember;
import com.example.backend.trip.domain.TripMemberRole;
import com.example.backend.trip.domain.TripMemberStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class TripMemberRepositoryTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Test
    @DisplayName("동일한 여행에 같은 사용자를 중복 등록하면 유니크 제약 조건 위반이 발생한다")
    void uniqueTripAndUserConstraint() {
        UUID userId = UUID.randomUUID();
        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .title("강릉 여행")
                .destination("강릉")
                .startDate(LocalDate.of(2026, 12, 5))
                .endDate(LocalDate.of(2026, 12, 7))
                .createdBy(userId)
                .build());

        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(userId)
                .role(TripMemberRole.OWNER)
                .build());

        assertThatThrownBy(() -> tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(userId)
                .role(TripMemberRole.MEMBER)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("여행별 활성 멤버와 OWNER를 조회할 수 있다")
    void findActiveMembersAndOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID activeMemberId = UUID.randomUUID();
        UUID leftMemberId = UUID.randomUUID();

        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .title("부산 여행")
                .destination("부산")
                .startDate(LocalDate.of(2026, 12, 12))
                .endDate(LocalDate.of(2026, 12, 14))
                .createdBy(ownerId)
                .build());

        tripMemberRepository.save(TripMember.builder()
                .trip(trip)
                .userId(ownerId)
                .role(TripMemberRole.OWNER)
                .build());
        tripMemberRepository.save(TripMember.builder()
                .trip(trip)
                .userId(activeMemberId)
                .role(TripMemberRole.MEMBER)
                .build());
        tripMemberRepository.save(TripMember.builder()
                .trip(trip)
                .userId(leftMemberId)
                .role(TripMemberRole.MEMBER)
                .status(TripMemberStatus.LEFT)
                .build());

        assertThat(tripMemberRepository.findAllByTripIdAndStatus(trip.getId(), TripMemberStatus.ACTIVE))
                .hasSize(2);
        assertThat(tripMemberRepository.existsByTripIdAndUserIdAndStatus(
                trip.getId(), activeMemberId, TripMemberStatus.ACTIVE)).isTrue();
        assertThat(tripMemberRepository.existsByTripIdAndUserIdAndStatus(
                trip.getId(), leftMemberId, TripMemberStatus.ACTIVE)).isFalse();
        assertThat(tripMemberRepository.findByTripIdAndRoleAndStatus(
                trip.getId(), TripMemberRole.OWNER, TripMemberStatus.ACTIVE))
                .isPresent()
                .get()
                .extracting(TripMember::getUserId)
                .isEqualTo(ownerId);
    }
}
