package com.travelmaker.backend.trip.repository;

import com.travelmaker.backend.trip.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TripRepositoryTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Test
    @DisplayName("Trip 저장 시 UUID와 타임스탬프가 자동 생성되고 수정 시 updatedAt이 갱신된다")
    void saveAndUpdateTrip() {
        UUID creatorId = UUID.randomUUID();
        Trip saved = tripRepository.saveAndFlush(Trip.builder()
                .title("제주도 가족 여행")
                .destination("제주시")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 4))
                .budget(2_000_000L)
                .createdBy(creatorId)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCurrency()).isEqualTo("KRW");
        assertThat(saved.getStatus()).isEqualTo(TripStatus.PLANNING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        saved.changeStatus(TripStatus.CONFIRMED);
        Trip updated = tripRepository.saveAndFlush(saved);
        assertThat(updated.getStatus()).isEqualTo(TripStatus.CONFIRMED);
    }

    @Test
    @DisplayName("생성자 및 참여자 기준으로 여행 목록을 조회할 수 있다")
    void findTripsByCreatorAndMember() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        Trip laterTrip = tripRepository.save(Trip.builder()
                .title("겨울 삿포로")
                .destination("삿포로")
                .startDate(LocalDate.of(2027, 1, 10))
                .endDate(LocalDate.of(2027, 1, 14))
                .createdBy(ownerId)
                .build());

        Trip earlierTrip = tripRepository.save(Trip.builder()
                .title("가을 교토")
                .destination("교토")
                .startDate(LocalDate.of(2026, 11, 20))
                .endDate(LocalDate.of(2026, 11, 23))
                .createdBy(ownerId)
                .build());

        tripMemberRepository.save(TripMember.builder()
                .trip(laterTrip)
                .userId(memberId)
                .role(TripMemberRole.MEMBER)
                .status(TripMemberStatus.ACTIVE)
                .build());
        tripMemberRepository.save(TripMember.builder()
                .trip(earlierTrip)
                .userId(memberId)
                .role(TripMemberRole.MEMBER)
                .status(TripMemberStatus.ACTIVE)
                .build());

        assertThat(tripRepository.findAllByCreatedBy(ownerId)).hasSize(2);

        List<Trip> memberTrips = tripRepository.findAllByMemberUserIdAndMemberStatus(
                memberId,
                TripMemberStatus.ACTIVE
        );
        assertThat(memberTrips)
                .extracting(Trip::getTitle)
                .containsExactly("가을 교토", "겨울 삿포로");
    }
}
