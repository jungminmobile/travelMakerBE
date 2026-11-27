package com.travelmaker.backend.trip.domain;

/**
 * 1:1 여행 초대의 처리 상태를 나타냅니다.
 */
public enum TripInvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED,
    EXPIRED
}
