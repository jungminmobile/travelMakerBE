package com.example.backend.generation.generator;

import com.example.backend.generation.client.AiServerClient;
import com.example.backend.generation.client.dto.AiItineraryRequest;
import com.example.backend.generation.client.dto.AiItineraryResponse;
import com.example.backend.generation.client.dto.AiItineraryResponse.AiDay;
import com.example.backend.generation.client.dto.AiItineraryResponse.AiPlace;
import com.example.backend.generation.client.dto.AiItineraryResponse.AiScheduleItem;
import com.example.backend.generation.client.dto.AiItineraryResponse.AiSeparateOption;
import com.example.backend.generation.dto.GeneratedItineraryPlan;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedDay;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedItem;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedPlace;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedSeparateOption;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 실제 AI 서버({@code POST /itineraries})와 연동하여 일정을 생성하는 구현체.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.generator", havingValue = "ai", matchIfMissing = true)
public class AiItineraryGenerator implements ItineraryGenerator {

    private static final String GENERATOR_NAME = "ai-server-itinerary-generator-v1";
    private static final int MAX_TITLE_LENGTH = 100;
    private static final String DEFAULT_TRANSPORT = "대중교통";
    private static final String DEFAULT_TEMPO = "적당히";
    private static final String DEFAULT_BUDGET = "50만원-100만원";

    private final AiServerClient aiServerClient;

    @Override
    public GeneratedItineraryPlan generate(GenerationContext context) {
        AiItineraryRequest request = buildRequest(context);
        AiItineraryResponse response = aiServerClient.generateItinerary(request);
        return toGeneratedPlan(context, request, response);
    }

    private AiItineraryRequest buildRequest(GenerationContext context) {
        List<AiItineraryRequest.Participant> participants = buildParticipants(context);
        AiItineraryRequest.Conditions conditions = buildConditions(context, participants.size());
        AiItineraryRequest.Options options = (context.maxSeparateBlocksPerDay() != null)
                ? new AiItineraryRequest.Options(context.maxSeparateBlocksPerDay())
                : null;

        return new AiItineraryRequest(
                blankToNull(context.countryDestination()),
                context.destination(),
                context.startDate().toString(),
                context.endDate().toString(),
                participants,
                conditions,
                options
        );
    }

    private List<AiItineraryRequest.Participant> buildParticipants(GenerationContext context) {
        List<AiItineraryRequest.Participant> participants = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();

        if (context.memberSnapshots() != null) {
            for (Map<String, Object> snapshot : context.memberSnapshots()) {
                Object rawUserId = snapshot.get("user_id");
                if (rawUserId == null) {
                    continue;
                }
                String userId = rawUserId.toString();
                if (!seenIds.add(userId)) {
                    continue;
                }
                String nickname = snapshot.get("nickname") instanceof String s && !s.isBlank()
                        ? s
                        : "참여자";
                Map<String, List<String>> quizSelections = resolveQuizSelections(snapshot);
                participants.add(new AiItineraryRequest.Participant(userId, nickname, quizSelections));
            }
        }

        if (participants.isEmpty() && context.memberUserIds() != null) {
            for (UUID memberId : context.memberUserIds()) {
                String id = memberId.toString();
                if (seenIds.add(id)) {
                    participants.add(new AiItineraryRequest.Participant(id, "참여자", Map.of()));
                }
            }
        }

        if (participants.isEmpty()) {
            throw new IllegalStateException("일정을 생성할 참여자 정보가 없습니다.");
        }
        return participants;
    }

    /**
     * 스냅샷에서 백엔드 퀴즈 응답({@code question_key} -> {@code selected_option_keys})을
     * 숫자 인덱스로 변환하지 않고 그대로 추출한다.
     */
    private Map<String, List<String>> resolveQuizSelections(Map<String, Object> snapshot) {
        Object rawQuizSelections = snapshot.get("quiz_selections");
        if (rawQuizSelections instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = entry.getKey().toString().trim();
                if (key.isEmpty()) {
                    continue;
                }
                List<String> values = toStringList(entry.getValue());
                result.put(key, values);
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        Object rawProfileData = snapshot.get("profile_data");
        if (rawProfileData instanceof Map<?, ?> profileData) {
            return extractFromAnswers(profileData.get("answers"));
        }
        return Map.of();
    }

    private Map<String, List<String>> extractFromAnswers(Object rawAnswers) {
        if (!(rawAnswers instanceof Collection<?> answers)) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Object item : answers) {
            if (!(item instanceof Map<?, ?> answerMap)) {
                continue;
            }
            Object questionKeyObj = answerMap.get("question_key");
            if (questionKeyObj == null) {
                continue;
            }
            String questionKey = questionKeyObj.toString().trim();
            if (!questionKey.isEmpty()) {
                result.put(questionKey, toStringList(answerMap.get("selected_option_keys")));
            }
        }
        return result;
    }

    private List<String> toStringList(Object rawValues) {
        if (!(rawValues instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : collection) {
            if (item != null && !item.toString().isBlank()) {
                values.add(item.toString().trim());
            }
        }
        return List.copyOf(values);
    }

    private AiItineraryRequest.Conditions buildConditions(GenerationContext context, int participantCount) {
        GenerationContext.Conditions raw = context.conditions();
        String defaultCompanion = participantCount <= 1 ? "혼자" : "친구";

        String companion = (raw != null && !isBlank(raw.companion())) ? raw.companion() : defaultCompanion;
        String transport = (raw != null && !isBlank(raw.transport())) ? raw.transport() : DEFAULT_TRANSPORT;
        String budget = (raw != null && !isBlank(raw.budget())) ? raw.budget() : mapBudgetLabel(context.budget());
        String tempo = (raw != null && !isBlank(raw.tempo())) ? raw.tempo() : DEFAULT_TEMPO;

        return new AiItineraryRequest.Conditions(companion, transport, budget, tempo);
    }

    private String mapBudgetLabel(Long budgetAmount) {
        if (budgetAmount == null) {
            return DEFAULT_BUDGET;
        }
        if (budgetAmount <= 500_000L) {
            return "50만원 이하";
        }
        if (budgetAmount <= 1_000_000L) {
            return "50만원-100만원";
        }
        if (budgetAmount <= 2_000_000L) {
            return "100만원-200만원";
        }
        if (budgetAmount <= 3_000_000L) {
            return "200만원-300만원";
        }
        return "300만원 이상";
    }

    private GeneratedItineraryPlan toGeneratedPlan(
            GenerationContext context,
            AiItineraryRequest request,
            AiItineraryResponse response
    ) {
        List<GeneratedDay> days = new ArrayList<>();
        Map<String, Integer> modeCounts = new LinkedHashMap<>();

        for (AiDay aiDay : response.days()) {
            int dayNumber = aiDay.day();
            LocalDate fallbackDate = context.startDate().plusDays(Math.max(0, dayNumber - 1L));
            LocalDate date = parseDate(aiDay.date(), fallbackDate);
            String dayTitle = isBlank(aiDay.title())
                    ? context.destination() + " " + dayNumber + "일차 코스"
                    : aiDay.title().trim();

            List<GeneratedItem> items = new ArrayList<>();
            if (aiDay.schedule() != null) {
                int order = 1;
                for (AiScheduleItem scheduleItem : aiDay.schedule()) {
                    String mode = blankToNull(scheduleItem.mode());
                    if (mode != null) {
                        modeCounts.merge(mode, 1, Integer::sum);
                    }
                    List<GeneratedSeparateOption> separateOptions = mapSeparateOptions(scheduleItem.separate());
                    String title = resolveItemTitle(scheduleItem, separateOptions);
                    items.add(new GeneratedItem(
                            null,
                            title,
                            scheduleItem.description(),
                            parseTime(scheduleItem.time()),
                            parseTime(scheduleItem.endTime()),
                            order++,
                            0L,
                            scheduleItem.reason(),
                            mapPlace(scheduleItem.place()),
                            mode,
                            scheduleItem.pickedBy(),
                            separateOptions
                    ));
                }
            }
            days.add(new GeneratedDay(dayNumber, date, clamp(dayTitle, MAX_TITLE_LENGTH), items));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("destination", response.destination());
        metadata.put("city", response.city());
        metadata.put("participant_count", request.participants().size());
        metadata.put("mode_counts", modeCounts);

        String planTitle = clamp(context.tripTitle() + " AI 추천 일정", MAX_TITLE_LENGTH);
        return new GeneratedItineraryPlan(planTitle, GENERATOR_NAME, days, metadata);
    }

    private List<GeneratedSeparateOption> mapSeparateOptions(List<AiSeparateOption> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return null;
        }
        List<GeneratedSeparateOption> mapped = new ArrayList<>(rawOptions.size());
        for (AiSeparateOption option : rawOptions) {
            mapped.add(new GeneratedSeparateOption(
                    option.participantIds() != null ? List.copyOf(option.participantIds()) : List.of(),
                    option.title(),
                    mapPlace(option.place()),
                    option.description(),
                    option.reason()
            ));
        }
        return mapped;
    }

    private GeneratedPlace mapPlace(AiPlace place) {
        if (place == null) {
            return null;
        }
        return new GeneratedPlace(
                blankToNull(place.id()),
                place.name(),
                place.lat(),
                place.lng(),
                blankToNull(place.concept())
        );
    }

    private String resolveItemTitle(AiScheduleItem item, List<GeneratedSeparateOption> separateOptions) {
        if (!isBlank(item.title())) {
            return item.title().trim();
        }
        if (separateOptions != null && !separateOptions.isEmpty()) {
            List<String> branchTitles = separateOptions.stream()
                    .map(GeneratedSeparateOption::title)
                    .filter(t -> !isBlank(t))
                    .map(String::trim)
                    .toList();
            if (!branchTitles.isEmpty()) {
                return String.join(" / ", branchTitles);
            }
        }
        if (item.place() != null && !isBlank(item.place().name())) {
            return item.place().name().trim();
        }
        return "추천 일정";
    }

    private LocalDate parseDate(String rawDate, LocalDate fallback) {
        if (isBlank(rawDate)) {
            return fallback;
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    private LocalTime parseTime(String rawTime) {
        if (isBlank(rawTime)) {
            return null;
        }
        try {
            return LocalTime.parse(rawTime.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String clamp(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
