package com.example.backend.generation.snapshot;

import com.example.backend.generation.domain.TripMemberSnapshot;
import com.example.backend.generation.repository.TripMemberSnapshotRepository;
import com.example.backend.preference.domain.UserPreferenceProfile;
import com.example.backend.preference.repository.UserPreferenceProfileRepository;
import com.example.backend.trip.domain.TripMember;
import com.example.backend.user.domain.User;
import com.example.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일정 생성 시점에 ACTIVE 참여자의 취향 프로파일을 {@code trip_member_snapshots}에 복제 저장하고
 * 백엔드 퀴즈 응답 형식 그대로 스냅샷 목록을 반환하는 어댑터.
 */
@Component
@RequiredArgsConstructor
public class DatabaseMemberPreferenceSnapshotAdapter implements MemberPreferenceSnapshotPort {

    private static final String DEFAULT_NICKNAME = "참여자";

    private final UserRepository userRepository;
    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final TripMemberSnapshotRepository tripMemberSnapshotRepository;

    @Override
    public List<Map<String, Object>> captureSnapshots(UUID tripId, List<TripMember> activeMembers) {
        if (activeMembers == null || activeMembers.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = activeMembers.stream()
                .map(TripMember::getUserId)
                .distinct()
                .toList();

        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));

        Map<UUID, UserPreferenceProfile> profilesByUserId = userPreferenceProfileRepository
                .findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserPreferenceProfile::getUserId, Function.identity(), (first, second) -> first));

        List<Map<String, Object>> results = new ArrayList<>(activeMembers.size());
        for (TripMember member : activeMembers) {
            User user = usersById.get(member.getUserId());
            String nickname = resolveNickname(user);
            UserPreferenceProfile profile = profilesByUserId.get(member.getUserId());

            Map<String, Object> profileDataCopy = copyProfileData(profile);
            Map<String, List<String>> quizSelections = extractQuizSelections(profileDataCopy);

            Map<String, Object> snapshotPayload = buildSnapshotPayload(profileDataCopy, quizSelections, profile != null);
            TripMemberSnapshot saved = tripMemberSnapshotRepository.save(TripMemberSnapshot.builder()
                    .tripId(tripId)
                    .userId(member.getUserId())
                    .profileSnapshot(snapshotPayload)
                    .sourceProfileVersion(profile != null ? profile.getVersion() : null)
                    .build());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("snapshot_id", saved.getId().toString());
            entry.put("user_id", member.getUserId().toString());
            entry.put("nickname", nickname);
            entry.put("role", member.getRole().name());
            entry.put("has_preference", profile != null);
            entry.put("profile_version", profile != null ? profile.getVersion() : null);
            entry.put("quiz_selections", quizSelections);
            entry.put("profile_data", profile != null ? profileDataCopy : Map.of());
            results.add(entry);
        }
        return results;
    }

    private String resolveNickname(User user) {
        if (user == null || user.getNickname() == null || user.getNickname().isBlank()) {
            return DEFAULT_NICKNAME;
        }
        return user.getNickname();
    }

    private Map<String, Object> copyProfileData(UserPreferenceProfile profile) {
        if (profile == null || profile.getProfileData() == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("answers", List.of());
            empty.put("selected_tags", List.of());
            return empty;
        }
        return new LinkedHashMap<>(profile.getProfileData());
    }

    private Map<String, Object> buildSnapshotPayload(
            Map<String, Object> profileDataCopy,
            Map<String, List<String>> quizSelections,
            boolean hasPreference
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (profileDataCopy.containsKey("quiz_version")) {
            payload.put("quiz_version", profileDataCopy.get("quiz_version"));
        }
        payload.put("answers", profileDataCopy.getOrDefault("answers", List.of()));
        payload.put("selected_tags", profileDataCopy.getOrDefault("selected_tags", List.of()));
        payload.put("quiz_selections", quizSelections);
        payload.put("has_preference", hasPreference);
        return payload;
    }

    /**
     * 백엔드 취향 프로파일의 answers(question_key, selected_option_keys)를
     * 숫자 인덱스로 변환하지 않고 키 그대로 Map 형태로 추출한다.
     */
    private Map<String, List<String>> extractQuizSelections(Map<String, Object> profileData) {
        Object rawAnswers = profileData.get("answers");
        if (!(rawAnswers instanceof Collection<?> answers)) {
            return Map.of();
        }
        Map<String, List<String>> selections = new LinkedHashMap<>();
        for (Object item : answers) {
            if (!(item instanceof Map<?, ?> answerMap)) {
                continue;
            }
            Object rawQuestionKey = answerMap.get("question_key");
            if (rawQuestionKey == null) {
                continue;
            }
            String questionKey = rawQuestionKey.toString().trim();
            if (questionKey.isEmpty()) {
                continue;
            }
            List<String> optionKeys = new ArrayList<>();
            Object rawOptions = answerMap.get("selected_option_keys");
            if (rawOptions instanceof Collection<?> options) {
                for (Object opt : options) {
                    if (opt != null && !opt.toString().isBlank()) {
                        optionKeys.add(opt.toString().trim());
                    }
                }
            }
            selections.put(questionKey, List.copyOf(optionKeys));
        }
        return selections;
    }
}
