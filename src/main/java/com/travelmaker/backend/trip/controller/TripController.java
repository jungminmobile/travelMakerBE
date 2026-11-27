package com.travelmaker.backend.trip.controller;

import com.travelmaker.backend.auth.security.AuthenticatedUser;
import com.travelmaker.backend.common.ApiResponse;
import com.travelmaker.backend.trip.dto.*;
import com.travelmaker.backend.trip.service.TripMemberService;
import com.travelmaker.backend.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips")

public class TripController {
    private final TripService tripService;
    private final TripMemberService tripMemberService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateTripResponse>> createTrip(
            @RequestBody CreateTripRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
            ){
        CreateTripResponse response = tripService.createTrip(currentUser.userId(),request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ApiResponse<List<TripSummaryResponse>> getMyTrips(
       @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(tripService.getMyTrips(currentUser.userId()));
    }

    @PatchMapping("/{tripId}")
    public ApiResponse<UpdateTripResponse> updateTrip(
            @PathVariable UUID tripId, @RequestBody UpdateTripRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
            ){
        return ApiResponse.ok(tripService.updateTrip(tripId,currentUser.userId(),request));
    }

    @DeleteMapping("/{tripId}/members/me")
    public ApiResponse<TripMemberResponse> leaveTrip(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(tripMemberService.leaveTrip(tripId,currentUser.userId()));
    }

    @DeleteMapping("/{tripId}/members/{userId}")
    public ApiResponse<TripMemberResponse> removeMember(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable("userId") UUID targetUserId
    ){
        return ApiResponse.ok(tripMemberService.removeMember(tripId,currentUser.userId(),targetUserId));
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> getTripDetail(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(tripService.getTripDetail(tripId,currentUser.userId()));
    }

    @DeleteMapping("/{tripId}")
    public ApiResponse<DeleteTripResponse> deleteTrip(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ){
        return ApiResponse.ok(tripService.deleteTrip(tripId,currentUser.userId()));
    }
}
