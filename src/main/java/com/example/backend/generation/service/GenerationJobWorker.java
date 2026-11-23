package com.example.backend.generation.service;

import com.example.backend.generation.domain.GenerationJob;
import com.example.backend.generation.domain.GenerationJobStatus;
import com.example.backend.generation.dto.GeneratedItineraryPlan;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedDay;
import com.example.backend.generation.dto.GeneratedItineraryPlan.GeneratedItem;
import com.example.backend.generation.event.GenerationJobCreatedEvent;
import com.example.backend.generation.generator.GenerationContext;
import com.example.backend.generation.generator.ItineraryGenerator;
import com.example.backend.generation.repository.GenerationJobRepository;
import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.domain.ItineraryDay;
import com.example.backend.itinerary.domain.ItineraryItem;
import com.example.backend.itinerary.domain.ItineraryStatus;
import com.example.backend.itinerary.repository.ItineraryDayRepository;
import com.example.backend.itinerary.repository.ItineraryItemRepository;
import com.example.backend.itinerary.repository.ItineraryRepository;
import com.example.backend.place.domain.Place;
import com.example.backend.place.repository.PlaceRepository;
import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 커밋된 일정 생성 작업을 백그라운드 스레드에서 실행하고 결과를 저장하는 워커.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationJobWorker {

    private static final int MAX_ITINERARY_TITLE_LENGTH = 100;
    private static final int MAX_DAY_TITLE_LENGTH = 100;
    private static final int MAX_ITEM_TITLE_LENGTH = 255;

    private final GenerationJobRepository generationJobRepository;
    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final PlaceRepository placeRepository;
    private final ItineraryGenerator itineraryGenerator;
    private final TransactionTemplate transactionTemplate;

    @Async("generationJobExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleJobCreated(GenerationJobCreatedEvent event) {
        execute(event.jobId());
    }

    /**
     * 작업을 RUNNING으로 전이한 뒤 생성기를 실행하고, 생성된 일정을 DB에 저장합니다.
     */
    public void execute(UUID jobId) {
        GenerationContext context = transactionTemplate.execute(status -> startJobAndBuildContext(jobId));
        if (context == null) {
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> updateJobProgress(jobId, 40));

            GeneratedItineraryPlan plan = itineraryGenerator.generate(context);

            transactionTemplate.executeWithoutResult(status -> completeJobWithItinerary(jobId, plan));
        } catch (Exception ex) {
            log.error("일정 생성 작업 실패 (jobId={})", jobId, ex);
            transactionTemplate.executeWithoutResult(status -> markJobFailed(jobId, ex));
        }
    }

    private GenerationContext startJobAndBuildContext(UUID jobId) {
        GenerationJob job = generationJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != GenerationJobStatus.PENDING) {
            return null;
        }

        job.start(Instant.now());

        Trip trip = tripRepository.findById(job.getTripId())
                .orElseThrow(() -> new IllegalStateException("여행 정보를 찾을 수 없습니다: " + job.getTripId()));

        return buildContext(trip, job.getInputData());
    }

    private void updateJobProgress(UUID jobId, int progressPercent) {
        generationJobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == GenerationJobStatus.RUNNING) {
                job.updateProgress(progressPercent);
            }
        });
    }

    private void completeJobWithItinerary(UUID jobId, GeneratedItineraryPlan plan) {
        if (plan == null || plan.days() == null || plan.days().isEmpty()) {
            throw new IllegalStateException("생성된 일정에 일차 정보가 없습니다.");
        }

        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("생성 작업을 찾을 수 없습니다: " + jobId));

        int nextVersion = itineraryRepository.findMaxVersionByTripId(job.getTripId()) + 1;
        String itineraryTitle = clamp(plan.title(), MAX_ITINERARY_TITLE_LENGTH);

        Itinerary itinerary = itineraryRepository.save(Itinerary.builder()
                .tripId(job.getTripId())
                .version(nextVersion)
                .title(itineraryTitle)
                .status(ItineraryStatus.DRAFT)
                .generationJobId(job.getId())
                .createdBy(job.getRequestedBy())
                .build());

        int totalItems = 0;
        for (GeneratedDay dayPlan : plan.days()) {
            ItineraryDay day = itineraryDayRepository.save(ItineraryDay.builder()
                    .itinerary(itinerary)
                    .dayNumber(dayPlan.dayNumber())
                    .date(dayPlan.date())
                    .title(clamp(dayPlan.title(), MAX_DAY_TITLE_LENGTH))
                    .build());

            if (dayPlan.items() == null) {
                continue;
            }
            for (GeneratedItem itemPlan : dayPlan.items()) {
                Place place = (itemPlan.placeId() == null)
                        ? null
                        : placeRepository.findById(itemPlan.placeId()).orElse(null);

                itineraryItemRepository.save(ItineraryItem.builder()
                        .itineraryDay(day)
                        .place(place)
                        .title(clamp(itemPlan.title(), MAX_ITEM_TITLE_LENGTH))
                        .description(itemPlan.description())
                        .startTime(itemPlan.startTime())
                        .endTime(itemPlan.endTime())
                        .displayOrder(itemPlan.displayOrder())
                        .estimatedCost(itemPlan.estimatedCost())
                        .recommendationReason(itemPlan.recommendationReason())
                        .build());
                totalItems++;
            }
        }

        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("itinerary_id", itinerary.getId().toString());
        resultData.put("version", itinerary.getVersion());
        resultData.put("title", itinerary.getTitle());
        resultData.put("generator", plan.generatorName());
        resultData.put("day_count", plan.days().size());
        resultData.put("item_count", totalItems);

        job.complete(resultData, Instant.now());
    }

    private void markJobFailed(UUID jobId, Exception ex) {
        generationJobRepository.findById(jobId)
                .ifPresent(job -> job.fail(resolveErrorMessage(ex), Instant.now()));
    }

    @SuppressWarnings("unchecked")
    private GenerationContext buildContext(Trip trip, Map<String, Object> inputData) {
        List<UUID> memberUserIds = List.of();
        Object rawMemberIds = inputData.get("member_user_ids");
        if (rawMemberIds instanceof List<?> list) {
            memberUserIds = list.stream()
                    .map(Object::toString)
                    .map(UUID::fromString)
                    .toList();
        }

        List<Map<String, Object>> memberSnapshots = List.of();
        Object rawSnapshots = inputData.get("member_snapshots");
        if (rawSnapshots instanceof List<?> list) {
            memberSnapshots = list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }

        String specialRequests = inputData.get("special_requests") instanceof String s ? s : null;
        boolean includeBudgetOptimization = Boolean.TRUE.equals(inputData.get("include_budget_optimization"));

        return new GenerationContext(
                trip.getId(),
                trip.getTitle(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getBudget(),
                trip.getCurrency(),
                memberUserIds,
                memberSnapshots,
                specialRequests,
                includeBudgetOptimization
        );
    }

    private String resolveErrorMessage(Exception ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? "일정 생성 중 오류가 발생했습니다." : message;
    }

    private String clamp(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
