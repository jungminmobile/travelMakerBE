package com.example.backend.preference.service;

import com.example.backend.preference.domain.UserPreferenceProfile;
import com.example.backend.preference.dto.PreferenceAnswerDto;
import com.example.backend.preference.dto.PreferenceProfileResponse;
import com.example.backend.preference.dto.UpsertPreferenceProfileRequest;
import com.example.backend.preference.repository.UserPreferenceProfileRepository;
import com.example.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 사용자 취향 프로파일 조회 및 저장(UPSERT) 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceProfileService {

    private static final String DEFAULT_QUIZ_VERSION = "v1.0";
    private static final String MODEL_NAME = "rule-based-v1";
    private static final int MAX_QUIZ_VERSION_LENGTH = 50;
    private static final int MAX_KEY_LENGTH = 100;

    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final UserRepository userRepository;

    /**
     * 현재 사용자의 취향 프로파일을 조회합니다.
     */
    public PreferenceProfileResponse getMyProfile(UUID userId) {
        UserPreferenceProfile profile = userPreferenceProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "취향 프로파일을 찾을 수 없습니다."));
        return PreferenceProfileResponse.from(profile);
    }

    /**
     * 취향 퀴즈 응답을 검증·정규화하여 단일 레코드로 저장 또는 갱신(UPSERT)합니다.
     */
    @Transactional
    public PreferenceProfileResponse upsertMyProfile(UUID userId, UpsertPreferenceProfileRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }

        Map<String, Object> profileData = validateAndBuildProfileData(request);

        UserPreferenceProfile saved = userPreferenceProfileRepository.findByUserId(userId)
                .map(existing -> {
                    existing.update(profileData, MODEL_NAME);
                    return userPreferenceProfileRepository.saveAndFlush(existing);
                })
                .orElseGet(() -> userPreferenceProfileRepository.saveAndFlush(
                        UserPreferenceProfile.builder()
                                .userId(userId)
                                .profileData(profileData)
                                .modelName(MODEL_NAME)
                                .build()
                ));

        return PreferenceProfileResponse.from(saved);
    }

    private Map<String, Object> validateAndBuildProfileData(UpsertPreferenceProfileRequest request) {
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answers는 비어 있을 수 없습니다.");
        }

        String quizVersion = normalizeQuizVersion(request.quizVersion());

        Set<String> seenQuestions = new HashSet<>();
        Set<String> selectedTags = new LinkedHashSet<>();
        List<Map<String, Object>> normalizedAnswers = new ArrayList<>(request.answers().size());

        for (PreferenceAnswerDto answer : request.answers()) {
            if (answer == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answer 항목은 null일 수 없습니다.");
            }
            String questionKey = normalizeKey(answer.questionKey(), "question_key");
            if (!seenQuestions.add(questionKey)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "중복된 question_key가 있습니다: " + questionKey);
            }
            if (answer.selectedOptionKeys() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "selected_option_keys는 null일 수 없습니다.");
            }

            Set<String> deduplicatedOptions = new LinkedHashSet<>();
            for (String rawOption : answer.selectedOptionKeys()) {
                String optionKey = normalizeKey(rawOption, "selected_option_keys 항목");
                deduplicatedOptions.add(optionKey);
                selectedTags.add(optionKey);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("question_key", questionKey);
            entry.put("selected_option_keys", List.copyOf(deduplicatedOptions));
            normalizedAnswers.add(entry);
        }

        if (selectedTags.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "최소 1개 이상의 선택지를 선택해야 합니다.");
        }

        Map<String, Object> profileData = new LinkedHashMap<>();
        profileData.put("quiz_version", quizVersion);
        profileData.put("answers", normalizedAnswers);
        profileData.put("selected_tags", List.copyOf(selectedTags));
        return profileData;
    }

    private String normalizeQuizVersion(String rawQuizVersion) {
        if (rawQuizVersion == null) {
            return DEFAULT_QUIZ_VERSION;
        }
        String trimmed = rawQuizVersion.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_QUIZ_VERSION_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "quiz_version은 1~" + MAX_QUIZ_VERSION_LENGTH + "자여야 합니다."
            );
        }
        return trimmed;
    }

    private String normalizeKey(String rawKey, String fieldName) {
        if (rawKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "는 필수입니다.");
        }
        String trimmed = rawKey.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + "는 1~" + MAX_KEY_LENGTH + "자여야 합니다."
            );
        }
        return trimmed;
    }
}
