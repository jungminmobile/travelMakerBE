package com.travelmaker.backend.trip.service;

import com.travelmaker.backend.preference.domain.UserPreferenceProfile;
import com.travelmaker.backend.preference.repository.UserPreferenceProfileRepository;
import com.travelmaker.backend.trip.domain.Trip;
import com.travelmaker.backend.trip.domain.TripMember;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import com.travelmaker.backend.trip.dto.*;
import com.travelmaker.backend.trip.repository.TripMemberRepository;
import com.travelmaker.backend.trip.repository.TripRepository;
import com.travelmaker.backend.user.domain.User;
import com.travelmaker.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class TripService {
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final UserPreferenceProfileRepository preferenceProfileRepository;

    @Transactional
    public CreateTripResponse createTrip(UUID userId, CreateTripRequest request){
        checkValidRequest(request.title(),request.destination(),request.startDate(),
                request.endDate(), request.budget(),request.currency());

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

    @Transactional
    public UpdateTripResponse updateTrip(UUID tripId, UUID userId, UpdateTripRequest request){
        if(!tripMemberRepository.existsByTripIdAndUserIdAndStatus(tripId,userId,TripMemberStatus.ACTIVE)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN," 해당 사용자는 여행에 가입되어 있지 않습니다.");
        }
        checkValidRequest(request.title(),request.destination(),request.startDate(),request.endDate(),
                request.budget(), request.currency());

        Trip trip = tripRepository.findById(tripId).orElseThrow(
                ()->new ResponseStatusException(HttpStatus.NOT_FOUND,"해당 여행은 없습니다."));

        trip.updateDetails(request.title(),request.destination(), request.startDate(),
                request.endDate(), request.budget(), request.currency());

        return UpdateTripResponse.from(trip);
    }

    private void checkValidRequest(String title, String destination, LocalDate startDate,
                                   LocalDate endDate, Long budget, String currency){
        if(title == null || destination == null ||
                startDate == null || endDate == null){
            throw badRequest("입력값이 유효하지 않습니다.");
        }

        if(title.isBlank() || title.length() > 100) {
            throw badRequest("제목은 공백 없이 100자 이하이여야 합니다.");
        }
        if(destination.isBlank() || destination.length() >255){
            throw badRequest("장소는 공백 없이 255자 이하여야 합니다.");
        }
        if(startDate.isAfter(endDate)){
            throw badRequest("도착 시간은 출발 시간보다 빠를순 없습니다.");
        }
        if(budget != null &&budget<0){
            throw badRequest("예산은 음수값이 될 수 없습니다.");
        }
        if(currency != null && currency.length() !=3){
            throw badRequest("금융 약어는 3글자입니다.");
        }
    }


    public TripDetailResponse getTripDetail(UUID tripId, UUID userId){
        if(!tripMemberRepository.existsByTripIdAndUserIdAndStatus(tripId,userId,TripMemberStatus.ACTIVE)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"해당 여행의 멤버가 아닙니다.");
        }

        Trip trip = tripRepository.findById(tripId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일치하는 여행이 없습니다."));

        List<TripMember> members = tripMemberRepository.findAllByTripIdAndStatus(tripId, TripMemberStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(TripMember::getJoinedAt))
                .toList();

        List<UUID> memberUserIds = members.stream().map(TripMember::getUserId).toList();

        Map<UUID, User> userMap = userRepository.findAllById(memberUserIds).stream()
                .collect(Collectors.toMap(User::getId,user->user));

        Set<UUID> preferenceUserIds = preferenceProfileRepository.findAllByUserIdIn(memberUserIds).stream()
                .map(UserPreferenceProfile::getUserId)
                .collect(Collectors.toSet());
        List<TripDetailResponse.TripDetailMemberResponse> memberResponses = members.stream()
                .map(member -> TripDetailResponse.TripDetailMemberResponse.
                        from(member, userMap.get(member.getUserId()),preferenceUserIds.contains(member.getUserId())))
                .toList();

        return TripDetailResponse.from(trip, memberResponses);
    }

}
