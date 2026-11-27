package com.travelmaker.backend.generation.generator;

import com.travelmaker.backend.generation.dto.GeneratedItineraryPlan;

/**
 * 여행 메타데이터와 참여자 조건을 바탕으로 일정을 생성하는 전략 인터페이스.
 */
public interface ItineraryGenerator {

    GeneratedItineraryPlan generate(GenerationContext context);
}
