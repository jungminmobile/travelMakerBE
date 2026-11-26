package com.example.backend.generation.generator;

import com.example.backend.generation.dto.GeneratedItineraryPlan;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedDay;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 실제 LLM 연동 전 파이프라인 검증용 Stub 일정 생성기.
 */
@Component
@ConditionalOnProperty(name = "app.ai.generator", havingValue = "stub")
public class StubItineraryGenerator implements ItineraryGenerator {

    private static final String GENERATOR_NAME = "stub-itinerary-generator-v1";
    private static final int MAX_TITLE_LENGTH = 100;

    @Override
    public GeneratedItineraryPlan generate(GenerationContext context) {
        List<GeneratedDay> days = new ArrayList<>();
        LocalDate current = context.startDate();
        int dayNumber = 1;

        while (!current.isAfter(context.endDate())) {
            days.add(buildDay(context, dayNumber, current));
            current = current.plusDays(1);
            dayNumber++;
        }

        String planTitle = clamp(context.tripTitle() + " 추천 일정", MAX_TITLE_LENGTH);
        return new GeneratedItineraryPlan(planTitle, GENERATOR_NAME, days);
    }

    private GeneratedDay buildDay(GenerationContext context, int dayNumber, LocalDate date) {
        String destination = context.destination();
        String reasonSuffix = buildReasonSuffix(context);
        long mealCost = resolveEstimatedCost(context, 18000L);
        long tourCost = resolveEstimatedCost(context, 15000L);

        List<GeneratedItem> items = List.of(
                new GeneratedItem(
                        null,
                        destination + " 오전 대표 명소 산책",
                        dayNumber + "일차 오전 추천 코스",
                        LocalTime.of(9, 30),
                        LocalTime.of(11, 30),
                        1,
                        tourCost,
                        "여행 기간과 목적지 중심의 오전 추천 동선" + reasonSuffix
                ),
                new GeneratedItem(
                        null,
                        destination + " 로컬 맛집 점심 식사",
                        dayNumber + "일차 점심 식사",
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 30),
                        2,
                        mealCost,
                        "지역 대표 식사를 포함한 휴식 일정" + reasonSuffix
                ),
                new GeneratedItem(
                        null,
                        destination + " 오후 문화·휴식 스팟",
                        dayNumber + "일차 오후 여유 코스",
                        LocalTime.of(14, 30),
                        LocalTime.of(17, 0),
                        3,
                        tourCost,
                        "무리 없는 이동 반경으로 구성한 오후 일정" + reasonSuffix
                )
        );

        String dayTitle = clamp(destination + " " + dayNumber + "일차 코스", MAX_TITLE_LENGTH);
        return new GeneratedDay(dayNumber, date, dayTitle, items);
    }

    private String buildReasonSuffix(GenerationContext context) {
        if (context.specialRequests() == null || context.specialRequests().isBlank()) {
            return "";
        }
        return " (요청 반영: " + context.specialRequests() + ")";
    }

    private long resolveEstimatedCost(GenerationContext context, long defaultCost) {
        if (context.includeBudgetOptimization() && context.budget() != null && context.budget() == 0L) {
            return 0L;
        }
        return defaultCost;
    }

    private String clamp(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
