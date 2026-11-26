package com.example.backend.generation.snapshot;

import com.example.backend.trip.domain.TripMember;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 일정 생성 요청 시 참여자 취향 프로파일 스냅샷을 복제·수집하기 위한 확장 포트.
 */
public interface MemberPreferenceSnapshotPort {

    List<Map<String, Object>> captureSnapshots(UUID tripId, List<TripMember> activeMembers);
}
