package com.example.backend.itinerary.service;

import com.example.backend.itinerary.dto.ItinerarySummaryResponse;
import com.example.backend.itinerary.repository.ItineraryRepository;
import com.example.backend.trip.domain.TripMemberStatus;
import com.example.backend.trip.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {
    private final ItineraryRepository itineraryRepository;
    private final TripMemberRepository tripMemberRepository;

    public List<ItinerarySummaryResponse> getItineraries(UUID tripId, UUID userId){
        if(!tripMemberRepository.existsByTripIdAndUserIdAndStatus(tripId,userId, TripMemberStatus.ACTIVE)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"여행 참여자만 접근할 수 있습니다.");
        }
        return itineraryRepository.findByTripIdOrderByVersionDesc(tripId).stream().map(ItinerarySummaryResponse::from).toList();
    }
}
