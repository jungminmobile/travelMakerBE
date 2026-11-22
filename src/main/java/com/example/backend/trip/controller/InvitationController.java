package com.example.backend.trip.controller;

import com.example.backend.auth.security.AuthenticatedUser;
import com.example.backend.common.ApiResponse;
import com.example.backend.trip.dto.CreateInvitationRequest;
import com.example.backend.trip.dto.CreateInvitationResponse;
import com.example.backend.trip.dto.InvitationResponse;
import com.example.backend.trip.service.InvitationService;
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
}
