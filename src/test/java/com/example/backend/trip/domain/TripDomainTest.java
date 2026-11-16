package com.example.backend.trip.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripDomainTest {

    @Test
    @DisplayName("Trip 생성 시 기본 currency는 KRW, 기본 status는 PLANNING이다")
    void tripDefaults() {
        UUID ownerId = UUID.randomUUID();

        Trip trip = Trip.builder()
                .title("도쿄 3박 4일")
                .destination("도쿄")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 4))
                .createdBy(ownerId)
                .build();

        assertThat(trip.getCurrency()).isEqualTo("KRW");
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PLANNING);
        assertThat(trip.getBudget()).isNull();
    }

    @Test
    @DisplayName("Trip 종료일이 시작일보다 빠르면 예외가 발생한다")
    void tripInvalidPeriod() {
        assertThatThrownBy(() -> Trip.builder()
                .title("잘못된 일정")
                .destination("부산")
                .startDate(LocalDate.of(2026, 12, 5))
                .endDate(LocalDate.of(2026, 12, 1))
                .createdBy(UUID.randomUUID())
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Trip 예산이 음수면 예외가 발생한다")
    void tripNegativeBudget() {
        assertThatThrownBy(() -> Trip.builder()
                .title("음수 예산")
                .destination("제주")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 2))
                .budget(-1000L)
                .createdBy(UUID.randomUUID())
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TripMember는 OWNER가 아니면 leave() 호출 시 LEFT 상태가 된다")
    void tripMemberLeave() {
        Trip trip = createSampleTrip();
        TripMember member = TripMember.builder()
                .trip(trip)
                .userId(UUID.randomUUID())
                .role(TripMemberRole.MEMBER)
                .build();

        member.leave();
        assertThat(member.getStatus()).isEqualTo(TripMemberStatus.LEFT);

        member.rejoin(TripMemberRole.MEMBER);
        assertThat(member.getStatus()).isEqualTo(TripMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("TripMember OWNER는 leave() 호출 시 예외가 발생한다")
    void tripOwnerCannotLeave() {
        Trip trip = createSampleTrip();
        TripMember owner = TripMember.builder()
                .trip(trip)
                .userId(trip.getCreatedBy())
                .role(TripMemberRole.OWNER)
                .build();

        assertThat(owner.isOwner()).isTrue();
        assertThatThrownBy(owner::leave)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TripInvitation은 userId와 email이 모두 없으면 생성할 수 없다")
    void tripInvitationRequiresInvitee() {
        Trip trip = createSampleTrip();

        assertThatThrownBy(() -> TripInvitation.builder()
                .trip(trip)
                .invitedBy(trip.getCreatedBy())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TripInvitation 수락/거절/취소/만료 상태 전이가 정상 동작한다")
    void tripInvitationTransitions() {
        Trip trip = createSampleTrip();
        Instant now = Instant.now();

        TripInvitation toAccept = TripInvitation.builder()
                .trip(trip)
                .invitedBy(trip.getCreatedBy())
                .inviteeUserId(UUID.randomUUID())
                .expiresAt(now.plus(3, ChronoUnit.DAYS))
                .build();
        toAccept.accept(now);
        assertThat(toAccept.getStatus()).isEqualTo(TripInvitationStatus.ACCEPTED);
        assertThat(toAccept.getRespondedAt()).isEqualTo(now);
        assertThatThrownBy(() -> toAccept.reject(now))
                .isInstanceOf(IllegalStateException.class);

        TripInvitation expired = TripInvitation.builder()
                .trip(trip)
                .invitedBy(trip.getCreatedBy())
                .inviteeEmail("friend@example.com")
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .build();
        assertThat(expired.isExpired(now)).isTrue();
        assertThatThrownBy(() -> expired.accept(now))
                .isInstanceOf(IllegalStateException.class);

        expired.expire();
        assertThat(expired.getStatus()).isEqualTo(TripInvitationStatus.EXPIRED);
    }

    private Trip createSampleTrip() {
        return Trip.builder()
                .title("오사카 먹방 여행")
                .destination("오사카")
                .startDate(LocalDate.of(2026, 12, 10))
                .endDate(LocalDate.of(2026, 12, 13))
                .budget(1_500_000L)
                .createdBy(UUID.randomUUID())
                .build();
    }
}
