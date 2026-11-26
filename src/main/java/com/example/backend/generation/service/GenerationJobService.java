package com.example.backend.generation.service;

import com.example.backend.generation.domain.GenerationJob;
import com.example.backend.generation.domain.GenerationJobStatus;
import com.example.backend.generation.dto.CreateGenerationJobRequest;
import com.example.backend.generation.dto.CreateGenerationJobResponse;
import com.example.backend.generation.dto.GenerationJobStatusResponse;
import com.example.backend.generation.dto.GenerationJobSummaryResponse;
import com.example.backend.generation.event.GenerationJobCreatedEvent;
import com.example.backend.generation.repository.GenerationJobRepository;
import com.example.backend.generation.snapshot.MemberPreferenceSnapshotPort;
import com.example.backend.itinerary.domain.Itinerary;
import com.example.backend.itinerary.repository.ItineraryRepository;
import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripMember;
import com.example.backend.trip.domain.TripMemberStatus;
import com.example.backend.trip.repository.TripMemberRepository;
import com.example.backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 일정 생성 작업 접수 및 상태/이력 조회 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationJobService {

    private static final int ESTIMATED_SECONDS = 15;
    private static final int MAX_SPECIAL_REQUESTS_LENGTH = 1000;
    private static final int MAX_DESTINATION_LENGTH = 100;
    private static final int MAX_CONDITION_LENGTH = 50;
    private static final int MAX_SEPARATE_BLOCKS_LIMIT = 10;
    private static final List<GenerationJobStatus> ACTIVE_STATUSES =
            List.of(GenerationJobStatus.PENDING, GenerationJobStatus.RUNNING);

    private final GenerationJobRepository generationJobRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ItineraryRepository itineraryRepository;
    private final MemberPreferenceSnapshotPort memberPreferenceSnapshotPort;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * AI 일정 생성 작업을 등록하고 커밋 이후 비동기 실행을 트리거합니다.
     */
    @Transactional
    public CreateGenerationJobResponse createJob(UUID tripId, UUID userId, CreateGenerationJobRequest request) {
        Trip trip = findTripAndValidateParticipant(tripId, userId);

        CreateGenerationJobRequest normalized = (request == null)
                ? new CreateGenerationJobRequest(null, null)
                : request;
        validateRequest(normalized);

        if (generationJobRepository.existsByTripIdAndStatusIn(tripId, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 진행 중인 일정 생성 작업이 있습니다."
            );
        }

        List<TripMember> activeMembers = tripMemberRepository.findAllByTripIdAndStatus(tripId, TripMemberStatus.ACTIVE);
        List<Map<String, Object>> snapshots = memberPreferenceSnapshotPort.captureSnapshots(tripId, activeMembers);

        Map<String, Object> inputData = buildInputData(trip, activeMembers, snapshots, normalized);
        GenerationJob saved = generationJobRepository.save(GenerationJob.builder()
                .tripId(tripId)
                .requestedBy(userId)
                .inputData(inputData)
                .build());

        applicationEventPublisher.publishEvent(new GenerationJobCreatedEvent(saved.getId()));
        return CreateGenerationJobResponse.from(saved, ESTIMATED_SECONDS);
    }

    /**
     * 단건 일정 생성 작업 상태를 조회합니다.
     */
    public GenerationJobStatusResponse getJob(UUID tripId, UUID jobId, UUID userId) {
        findTripAndValidateParticipant(tripId, userId);

        GenerationJob job = generationJobRepository.findByIdAndTripId(jobId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "생성 작업을 찾을 수 없습니다."));

        UUID itineraryId = null;
        if (job.getStatus() == GenerationJobStatus.COMPLETED) {
            itineraryId = itineraryRepository.findByGenerationJobId(job.getId())
                    .map(Itinerary::getId)
                    .orElse(null);
        }
        return GenerationJobStatusResponse.of(job, itineraryId);
    }

    /**
     * 여행의 모든 일정 생성 작업 이력을 최신순으로 조회합니다.
     */
    public List<GenerationJobSummaryResponse> getJobs(UUID tripId, UUID userId) {
        findTripAndValidateParticipant(tripId, userId);

        List<GenerationJob> jobs = generationJobRepository.findByTripIdOrderByCreatedAtDesc(tripId);
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<UUID> jobIds = jobs.stream().map(GenerationJob::getId).toList();
        Map<UUID, UUID> itineraryIdByJobId = itineraryRepository.findAllByGenerationJobIdIn(jobIds).stream()
                .collect(Collectors.toMap(
                        Itinerary::getGenerationJobId,
                        Itinerary::getId,
                        (first, second) -> first
                ));

        return jobs.stream()
                .map(job -> GenerationJobSummaryResponse.of(job, itineraryIdByJobId.get(job.getId())))
                .toList();
    }

    private void validateRequest(CreateGenerationJobRequest request) {
        String specialRequests = request.specialRequests();
        if (specialRequests != null && specialRequests.length() > MAX_SPECIAL_REQUESTS_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "special_requests는 " + MAX_SPECIAL_REQUESTS_LENGTH + "자 이하여야 합니다."
            );
        }

        validateOptionalText(request.destination(), "destination", MAX_DESTINATION_LENGTH);

        CreateGenerationJobRequest.Conditions conditions = request.conditions();
        if (conditions != null) {
            validateOptionalText(conditions.companion(), "conditions.companion", MAX_CONDITION_LENGTH);
            validateOptionalText(conditions.transport(), "conditions.transport", MAX_CONDITION_LENGTH);
            validateOptionalText(conditions.budget(), "conditions.budget", MAX_CONDITION_LENGTH);
            validateOptionalText(conditions.tempo(), "conditions.tempo", MAX_CONDITION_LENGTH);
        }

        CreateGenerationJobRequest.Options options = request.options();
        if (options != null && options.maxSeparateBlocksPerDay() != null) {
            int maxSeparate = options.maxSeparateBlocksPerDay();
            if (maxSeparate < 0 || maxSeparate > MAX_SEPARATE_BLOCKS_LIMIT) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "max_separate_blocks_per_day는 0 이상 " + MAX_SEPARATE_BLOCKS_LIMIT + " 이하여야 합니다."
                );
            }
        }
    }

    private void validateOptionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + "는 1~" + maxLength + "자여야 합니다."
            );
        }
    }

    private Trip findTripAndValidateParticipant(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
        if (!tripMemberRepository.existsByTripIdAndUserIdAndStatus(tripId, userId, TripMemberStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "여행 참여자만 접근할 수 있습니다.");
        }
        return trip;
    }

    private Map<String, Object> buildInputData(
            Trip trip,
            List<TripMember> activeMembers,
            List<Map<String, Object>> snapshots,
            CreateGenerationJobRequest request
    ) {
        Map<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("trip_id", trip.getId().toString());
        inputData.put("title", trip.getTitle());
        inputData.put("destination", trip.getDestination());
        inputData.put("start_date", trip.getStartDate().toString());
        inputData.put("end_date", trip.getEndDate().toString());
        inputData.put("budget", trip.getBudget());
        inputData.put("currency", trip.getCurrency());
        inputData.put("member_user_ids", activeMembers.stream()
                .map(member -> member.getUserId().toString())
                .toList());
        inputData.put("special_requests", request.specialRequests());
        inputData.put("include_budget_optimization", Boolean.TRUE.equals(request.includeBudgetOptimization()));
        if (request.destination() != null) {
            inputData.put("country_destination", request.destination().trim());
        }
        if (request.conditions() != null) {
            Map<String, Object> conditionsMap = new LinkedHashMap<>();
            putIfPresent(conditionsMap, "companion", request.conditions().companion());
            putIfPresent(conditionsMap, "transport", request.conditions().transport());
            putIfPresent(conditionsMap, "budget", request.conditions().budget());
            putIfPresent(conditionsMap, "tempo", request.conditions().tempo());
            if (!conditionsMap.isEmpty()) {
                inputData.put("conditions", conditionsMap);
            }
        }
        if (request.options() != null && request.options().maxSeparateBlocksPerDay() != null) {
            inputData.put("options", Map.of(
                    "max_separate_blocks_per_day", request.options().maxSeparateBlocksPerDay()
            ));
        }
        inputData.put("member_snapshots", snapshots);
        return inputData;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value.trim());
        }
    }
}
