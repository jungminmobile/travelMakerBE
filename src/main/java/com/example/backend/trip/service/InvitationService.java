package com.example.backend.trip.service;

import com.example.backend.trip.domain.*;
import com.example.backend.trip.dto.CreateInvitationRequest;
import com.example.backend.trip.dto.CreateInvitationResponse;
import com.example.backend.trip.dto.InvitationResponse;
import com.example.backend.trip.repository.TripInvitationRepository;
import com.example.backend.trip.repository.TripMemberRepository;
import com.example.backend.trip.repository.TripRepository;
import com.example.backend.user.domain.User;
import com.example.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class InvitationService {
    private final TripInvitationRepository tripInvitationRepository;
    private final UserRepository userRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripRepository tripRepository;

    //유효한 초대 목록보기
    public List<InvitationResponse> getMyInvitations (UUID userId){
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        Instant now = Instant.now();
        List<TripInvitation> tripInvitationList = tripInvitationRepository.findAllValidByInviteeWithTrip(TripInvitationStatus.PENDING,now,userId,user.getEmail());
        return tripInvitationList.stream()
                .map(InvitationResponse::from)
                .toList();
    }

    //초대 요청하기
    @Transactional
    public CreateInvitationResponse createInvitation(UUID tripId, UUID userId,
                                               CreateInvitationRequest request){
        if(!tripMemberRepository.existsByTripIdAndUserIdAndStatus(tripId,userId, TripMemberStatus.ACTIVE)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"초대 권한이 없습니다.");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 여행입니다."));

        if((request.inviteeUserId() == null && request.inviteeEmail() == null) ||
                (request.inviteeEmail() != null && request.inviteeEmail().isBlank())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"잘못된 요청입니다.");
        }
        // 검사 대상 사용자 id: 요청에 id가 있으면 존재 확인, 없으면 이메일로 가입자를 찾는다(미가입이면 null)
        UUID targetUserId = request.inviteeUserId();
        if (targetUserId != null) {
            if (!userRepository.existsById(targetUserId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 유저 입니다. 가입할 이메일을 입력하세요");
            }
        } else {
            targetUserId = userRepository.findByEmail(request.inviteeEmail())
                    .map(User::getId)
                    .orElse(null);
        }

        // 가입자인 경우: 이미 참여 중이거나 사용자 기준으로 이미 초대한 경우
        if (targetUserId != null
                && (tripMemberRepository.existsByTripIdAndUserIdAndStatus(tripId, targetUserId, TripMemberStatus.ACTIVE)
                || tripInvitationRepository.existsByTripIdAndInviteeUserIdAndStatus(tripId, targetUserId, TripInvitationStatus.PENDING))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 참여 중이거나 초대된 사용자입니다.");
        }

        // 이메일 기준으로 이미 초대한 경우 (가입 여부와 무관)
        if (request.inviteeEmail() != null
                && tripInvitationRepository.existsByTripIdAndInviteeEmailAndStatus(tripId, request.inviteeEmail(), TripInvitationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 초대된 사용자입니다.");
        }

        TripInvitation tripInvitation = TripInvitation.builder().trip(trip).invitedBy(userId).inviteeUserId(request.inviteeUserId())
                .inviteeEmail(request.inviteeEmail()).role(TripMemberRole.MEMBER).status(TripInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)).build();
        TripInvitation saved =  tripInvitationRepository.save(tripInvitation);

        return CreateInvitationResponse.from(saved);
    }
}
