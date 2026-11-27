package com.travelmaker.backend.trip.service;

import com.travelmaker.backend.trip.domain.TripMember;
import com.travelmaker.backend.trip.domain.TripMemberRole;
import com.travelmaker.backend.trip.domain.TripMemberStatus;
import com.travelmaker.backend.trip.dto.TripMemberResponse;
import com.travelmaker.backend.trip.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class TripMemberService {
    private static final String NOT_A_MEMBER = "해당 여행의 멤버가 아닙니다.";
    private static final String TARGET_NOT_FOUND = "대상 멤버를 찾을 수 없습니다.";

    private final TripMemberRepository tripMemberRepository;

    @Transactional
    public TripMemberResponse leaveTrip(UUID tripId, UUID userId){
        TripMember tripMember = tripMemberRepository.findByTripIdAndUserId(tripId,userId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_A_MEMBER));

        if(tripMember.getStatus() == TripMemberStatus.LEFT){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_A_MEMBER);
        }
        if(tripMember.getRole() == TripMemberRole.OWNER){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "방장은 여행을 나갈 수 없습니다.");
        }
        tripMember.leave();

        return TripMemberResponse.from(tripMember);
    }

    @Transactional
    public TripMemberResponse removeMember(UUID tripId, UUID ownerId, UUID targetUserId){
        TripMember owner = tripMemberRepository.findByTripIdAndUserId(tripId,ownerId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_A_MEMBER));
        if(owner.getStatus() == TripMemberStatus.LEFT){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_A_MEMBER);
        }
        if(owner.getRole() != TripMemberRole.OWNER){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "방장만 참여자를 내보낼 수 있습니다.");
        }

        TripMember targetUser = tripMemberRepository.findByTripIdAndUserId(tripId,targetUserId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, TARGET_NOT_FOUND));

        if(targetUser.getRole() == TripMemberRole.OWNER){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "방장은 내보낼 수 없습니다.");
        }
        if(targetUser.getStatus() == TripMemberStatus.LEFT){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, TARGET_NOT_FOUND);
        }
        targetUser.leave();
        return TripMemberResponse.from(targetUser);
    }
}
