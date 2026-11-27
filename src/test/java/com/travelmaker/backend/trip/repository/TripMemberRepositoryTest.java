package com.travelmaker.backend.trip.repository;

import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripMember;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class TripMemberRepositoryTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    @DisplayName("내가 ACTIVE인 멤버십만 여행과 함께 시작일 순으로 조회한다")
    void findAllByUserIdAndStatusWithTrip() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Trip jeju = saveTrip("제주 여행", LocalDate.of(2026, 12, 10), other);
        Trip busan = saveTrip("부산 여행", LocalDate.of(2026, 12, 1), other);
        Trip left = saveTrip("나간 여행", LocalDate.of(2026, 12, 5), other);
        Trip notMine = saveTrip("남의 여행", LocalDate.of(2026, 12, 3), other);

        saveMember(jeju, me, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(busan, me, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);
        saveMember(left, me, TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        saveMember(notMine, other, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);

        // 영속성 컨텍스트를 비워서 trip이 정말 함께 조회되는지(fetch join) 확인한다.
        entityManager.flush();
        entityManager.clear();

        List<TripMember> result = tripMemberRepository.findAllByUserIdAndStatusWithTrip(me, TripMemberStatus.ACTIVE);

        assertThat(result).extracting(member -> member.getTrip().getTitle())
                .containsExactly("부산 여행", "제주 여행");
        assertThat(result).extracting(TripMember::getRole)
                .containsExactly(TripMemberRole.MEMBER, TripMemberRole.OWNER);
        assertThat(result).allSatisfy(member ->
                assertThat(Hibernate.isInitialized(member.getTrip())).isTrue());
    }

    @Test
    @DisplayName("여행별 ACTIVE 멤버 수를 한 번에 센다")
    void countByTripIdsAndStatus() {
        UUID owner = UUID.randomUUID();

        Trip three = saveTrip("세 명 여행", LocalDate.of(2026, 12, 1), owner);
        Trip one = saveTrip("한 명 여행", LocalDate.of(2026, 12, 2), owner);
        Trip onlyLeft = saveTrip("나간 사람만 있는 여행", LocalDate.of(2026, 12, 3), owner);
        Trip notRequested = saveTrip("조회하지 않은 여행", LocalDate.of(2026, 12, 4), owner);

        saveMember(three, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(three, UUID.randomUUID(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);
        saveMember(three, UUID.randomUUID(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);
        saveMember(three, UUID.randomUUID(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        saveMember(one, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(onlyLeft, UUID.randomUUID(), TripMemberRole.MEMBER, TripMemberStatus.LEFT);
        saveMember(notRequested, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE);
        saveMember(notRequested, UUID.randomUUID(), TripMemberRole.MEMBER, TripMemberStatus.ACTIVE);

        List<Object[]> rows = tripMemberRepository.countByTripIdsAndStatus(
                List.of(three.getId(), one.getId(), onlyLeft.getId()), TripMemberStatus.ACTIVE);
        Map<UUID, Long> counts = rows.stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        assertThat(counts)
                .containsEntry(three.getId(), 3L)
                .containsEntry(one.getId(), 1L)
                .doesNotContainKey(onlyLeft.getId())      // ACTIVE가 없는 여행은 결과에 없다
                .doesNotContainKey(notRequested.getId()); // 요청하지 않은 여행도 없다
    }

    private Trip saveTrip(String title, LocalDate startDate, UUID createdBy) {
        return tripRepository.saveAndFlush(Trip.builder()
                .title(title)
                .destination("국내")
                .startDate(startDate)
                .endDate(startDate.plusDays(2))
                .createdBy(createdBy)
                .build());
    }

    private void saveMember(Trip trip, UUID userId, TripMemberRole role, TripMemberStatus status) {
        tripMemberRepository.saveAndFlush(TripMember.builder()
                .trip(trip)
                .userId(userId)
                .role(role)
                .status(status)
                .build());
    }
}
