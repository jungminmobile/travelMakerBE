package com.example.backend.generation.snapshot;

import com.example.backend.trip.domain.TripMember;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 취향 프로파일(U2) 연동 전까지 빈 스냅샷 목록을 반환하는 기본 어댑터.
 */
@Component
public class NoOpMemberPreferenceSnapshotAdapter implements MemberPreferenceSnapshotPort {

    @Override
    public List<Map<String, Object>> captureSnapshots(UUID tripId, List<TripMember> activeMembers) {
        return List.of();
    }
}
