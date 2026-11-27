package com.travelmaker.backend.trip.controller;

import com.travelmaker.backend.auth.security.AuthenticatedUser;
import com.travelmaker.backend.common.ApiResponse;
import com.travelmaker.backend.trip.dto.*;
import com.travelmaker.backend.trip.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")

public class InvitationController {
    private final InvitationService invitationService;

    @PostMapping("/trips/{tripId}/invitations")
    public ResponseEntity<ApiResponse<CreateInvitationResponse>> createInvitation(
            @PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody CreateInvitationRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(invitationService.createInvitation(
                tripId, currentUser.userId(),request
        )));
    }

    @GetMapping("/invitations/me")
    public ApiResponse<List<InvitationResponse>> getMyInvitation(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(invitationService.getMyInvitations(currentUser.userId()));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ApiResponse<AcceptInvitationResponse> acceptInvitation(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(invitationService.acceptInvitation(invitationId,currentUser.userId()));
    }

    @PostMapping("/invitations/{invitationId}/reject")
    public ApiResponse<RejectInvitationResponse> rejectInvitation(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(invitationService.rejectInvitation(invitationId,currentUser.userId()));
    }

    @GetMapping("/trips/{tripId}/invitations")
    public ApiResponse<List<SentInvitationResponse>> getTripInvitations(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(invitationService.getSentInvitations(tripId, currentUser.userId()));
    }

    @DeleteMapping("/trips/{tripId}/invitations/{invitationId}")
    public ApiResponse<CancelInvitationResponse> cancelInvitation(
            @PathVariable UUID tripId,
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(invitationService.cancelInvitation(tripId,currentUser.userId(),invitationId));
    }
}
