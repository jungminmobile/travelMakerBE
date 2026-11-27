package com.travelmaker.backend.itinerary.service;

import com.travelmaker.backend.itinerary.domain.Itinerary;
import com.travelmaker.backend.itinerary.domain.ItineraryDay;
import com.travelmaker.backend.itinerary.domain.ItineraryItem;
import com.travelmaker.backend.itinerary.domain.ItineraryStatus;
import com.travelmaker.backend.itinerary.dto.*;
import com.travelmaker.backend.itinerary.repository.ItineraryDayRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryItemRepository;
import com.travelmaker.backend.itinerary.repository.ItineraryRepository;
import com.travelmaker.backend.place.domain.Place;
import com.travelmaker.backend.place.repository.PlaceRepository;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import com.travelmaker.backend.trip.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_ITEM_TITLE_LENGTH = 255;

    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final TripMemberRepository tripMemberRepository;
    private final PlaceRepository placeRepository;

    public List<ItinerarySummaryResponse> getItineraries(UUID tripId, UUID userId){
        validateParticipant(tripId, userId);
        return itineraryRepository.findByTripIdOrderByVersionDesc(tripId).stream()
                .map(ItinerarySummaryResponse::from)
                .toList();
    }

    /**
     * 일정 상세 조회. 일차와 항목은 각각 한 번의 쿼리로 가져와 메모리에서 묶는다.
     */
    public ItineraryDetailResponse getItinerary(UUID tripId, UUID itineraryId, UUID userId) {
        validateParticipant(tripId, userId);
        Itinerary itinerary = findItinerary(tripId, itineraryId);

        List<ItineraryDay> days = itineraryDayRepository.findByItineraryIdOrderByDayNumberAsc(itineraryId);
        Map<UUID, List<ItineraryItem>> itemsByDayId = itineraryItemRepository
                .findAllByItineraryIdWithPlace(itineraryId).stream()
                .collect(Collectors.groupingBy(item -> item.getItineraryDay().getId()));

        return ItineraryDetailResponse.of(itinerary, days, itemsByDayId);
    }

    /**
     * 일정 이름/상태 수정. ACTIVE로의 변경은 확정 API로만 가능하다.
     */
    @Transactional
    public ItinerarySummaryResponse updateItinerary(UUID tripId, UUID itineraryId, UUID userId,
                                                    UpdateItineraryRequest request) {
        validateParticipant(tripId, userId);
        Itinerary itinerary = findItinerary(tripId, itineraryId);

        String title = request.title();
        ItineraryStatus status = request.status();
        if (title == null && status == null) {
            throw badRequest("수정할 title 또는 status를 보내 주세요.");
        }
        if (title != null && (title.isBlank() || title.length() > MAX_TITLE_LENGTH)) {
            throw badRequest("title은 비어 있을 수 없고 " + MAX_TITLE_LENGTH + "자 이하여야 합니다.");
        }
        if (status == ItineraryStatus.ACTIVE) {
            throw badRequest("ACTIVE로 변경하려면 확정 API를 사용해 주세요.");
        }

        if (title != null) {
            itinerary.rename(title);
        }
        if (status != null) {
            itinerary.changeStatus(status);
        }
        return ItinerarySummaryResponse.from(itinerary);
    }

    /**
     * 일정 확정. 같은 여행의 기존 ACTIVE 일정은 같은 트랜잭션에서 ARCHIVED로 바꾼다.
     * 이미 ACTIVE인 일정을 다시 확정해도 결과는 같다.
     */
    @Transactional
    public ActivateItineraryResponse activate(UUID tripId, UUID itineraryId, UUID userId) {
        validateParticipant(tripId, userId);
        Itinerary target = findItinerary(tripId, itineraryId);

        if (target.getStatus() != ItineraryStatus.ACTIVE) {
            itineraryRepository.findAllByTripIdAndStatus(tripId, ItineraryStatus.ACTIVE)
                    .forEach(current -> current.changeStatus(ItineraryStatus.ARCHIVED));
            target.changeStatus(ItineraryStatus.ACTIVE);
        }
        return ActivateItineraryResponse.from(target);
    }

    private void validateParticipant(UUID tripId, UUID userId) {
        if (!tripMemberRepository.existsByTripIdAndUserIdAndStatus(tripId, userId, TripMemberStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "여행 참여자만 접근할 수 있습니다.");
        }
    }

    /**
     * 다른 여행의 일정 id로 접근하지 못하도록 tripId까지 함께 조건으로 조회한다.
     */
    private Itinerary findItinerary(UUID tripId, UUID itineraryId) {
        return itineraryRepository.findByIdAndTripId(itineraryId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ItineraryItem findItem(UUID itemId, UUID tripId){
        ItineraryItem itineraryItem = itineraryItemRepository.findByIdAndItineraryDay_Itinerary_TripId(itemId, tripId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정 항목을 찾을 수 없습니다.")
        );
        return itineraryItem;
    }

    @Transactional
    public UpdateItineraryItemResponse updateItem(UUID tripId, UUID itemId, UUID userId, UpdateItineraryItemRequest request){
        validateParticipant(tripId,userId);

        ItineraryItem target = findItem(itemId,tripId);
        if(request.startTime() == null && request.endTime() == null && request.estimatedCost() == null){
            throw badRequest("수정할 start_time, end_time 또는 estimated_cost를 보내 주세요.");
        }
        if(request.estimatedCost() != null &&request.estimatedCost() < 0){
            throw badRequest("estimated_cost는 0 이상이어야 합니다.");
        }

        LocalTime startTime = (request.startTime() != null) ? request.startTime() : target.getStartTime();
        LocalTime endTime = (request.endTime() != null) ? request.endTime() : target.getEndTime();
        Long estimatedCost = (request.estimatedCost() != null) ? request.estimatedCost() : target.getEstimatedCost();

        if(startTime != null && endTime != null && startTime.isAfter(endTime)){
            throw badRequest("start_time은 end_time보다 늦을 수 없습니다.");
        }

        target.update(startTime,endTime,target.getDisplayOrder(),estimatedCost);

        return UpdateItineraryItemResponse.from(target);
    }

    /**
     * 일정 항목 추가. 일정이 이 여행의 것이고, 일차가 그 일정의 것인지 확인한 뒤 저장한다.
     * place_id가 있으면 실제 존재하는 장소여야 한다.
     */
    @Transactional
    public CreateItineraryItemResponse addItem(UUID tripId, UUID itineraryId, UUID dayId, UUID userId,
                                               CreateItineraryItemRequest request) {
        validateParticipant(tripId, userId);
        findItinerary(tripId, itineraryId);
        ItineraryDay day = itineraryDayRepository.findByIdAndItineraryId(dayId, itineraryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일차를 찾을 수 없습니다."));

        String title = request.title();
        if (title == null || title.isBlank() || title.length() > MAX_ITEM_TITLE_LENGTH) {
            throw badRequest("title은 비어 있을 수 없고 " + MAX_ITEM_TITLE_LENGTH + "자 이하여야 합니다.");
        }
        if (request.displayOrder() == null || request.displayOrder() < 0) {
            throw badRequest("display_order는 0 이상의 값이어야 합니다.");
        }
        if (request.estimatedCost() != null && request.estimatedCost() < 0) {
            throw badRequest("estimated_cost는 0 이상이어야 합니다.");
        }
        if (request.startTime() != null && request.endTime() != null
                && request.startTime().isAfter(request.endTime())) {
            throw badRequest("start_time은 end_time보다 늦을 수 없습니다.");
        }

        Place place = null;
        if (request.placeId() != null) {
            place = placeRepository.findById(request.placeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."));
        }

        ItineraryItem item = itineraryItemRepository.save(ItineraryItem.builder()
                .itineraryDay(day)
                .place(place)
                .title(title)
                .description(request.description())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .displayOrder(request.displayOrder())
                .estimatedCost(request.estimatedCost())
                .build());
        return CreateItineraryItemResponse.from(item);
    }

    /**
     * 일정 항목 삭제. 항목이 이 여행의 것이 아니면 404.
     */
    @Transactional
    public void deleteItem(UUID tripId, UUID itemId, UUID userId) {
        validateParticipant(tripId, userId);
        itineraryItemRepository.delete(findItem(itemId, tripId));
    }
}
