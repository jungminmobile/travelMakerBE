package com.example.backend.trip.service;

import com.example.backend.trip.domain.Trip;
import com.example.backend.trip.domain.TripMember;
import com.example.backend.trip.domain.TripMemberRole;
import com.example.backend.trip.domain.TripMemberStatus;
import com.example.backend.trip.dto.CreateTripRequest;
import com.example.backend.trip.dto.CreateTripResponse;
import com.example.backend.trip.dto.TripSummaryResponse;
import com.example.backend.trip.repository.TripMemberRepository;
import com.example.backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class TripService {
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    @Transactional
    public CreateTripResponse createTrip(UUID userId, CreateTripRequest request){


        if(request.title() == null || request.destination() == null ||
        request.startDate() == null || request.endDate() == null){
            throw badRequest("입력값이 유효하지 않습니다.");
        }

        if(request.title().isBlank() || request.title().length() > 100) {
            throw badRequest("제목은 공백 없이 100자 이하이여야 합니다.");
        }
        if(request.destination().isBlank() || request.destination().length() >255){
            throw badRequest("장소는 공백 없이 255자 이하여야 합니다.");
        }
        if(request.startDate().isAfter(request.endDate())){
            throw badRequest("도착 시간은 출발 시간보다 빠를순 없습니다.");
        }
        if(request.budget() != null &&request.budget()<0){
            throw badRequest("예산은 음수값이 될 수 없습니다.");
        }
        if(request.currency() != null && request.currency().length() !=3){
            throw badRequest("금융 약어는 3글자입니다.");
        }
        Trip trip = Trip.builder().title(request.title()).destination(request.destination()).
                startDate(request.startDate()).endDate(request.endDate()).budget(request.budget()).
                currency(request.currency()).createdBy(userId).build();
        tripRepository.save(trip);

        TripMember member = TripMember.builder().trip(trip).userId(userId).role(TripMemberRole.OWNER).
                status(TripMemberStatus.ACTIVE).build();
        tripMemberRepository.save(member);
        return CreateTripResponse.from(trip);
    }

    private ResponseStatusException badRequest(String message){
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }

    public List<TripSummaryResponse> getMyTrips(UUID userId){
        List<TripMember> memberlists = tripMemberRepository.findAllByUserIdAndStatusWithTrip(userId, TripMemberStatus.ACTIVE);

        if(memberlists.isEmpty()){
            return Collections.emptyList();
        }
        List<UUID> tripIds = memberlists.stream().map(member -> member.getTrip().getId()).collect(Collectors.toList());
        Map<UUID, Long> counts = tripMemberRepository.countByTripIdsAndStatus(tripIds, TripMemberStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return memberlists.stream()
                .map(member -> TripSummaryResponse.of(
                        member.getTrip(),
                        member.getRole(),
                        counts.getOrDefault(member.getTrip().getId(), 0L)
                ))
                .toList();
    }
}
