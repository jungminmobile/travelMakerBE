package com.example.backend.trip.controller;

import com.example.backend.auth.security.AuthenticatedUser;
import com.example.backend.common.ApiResponse;
import com.example.backend.trip.dto.CreateTripRequest;
import com.example.backend.trip.dto.CreateTripResponse;
import com.example.backend.trip.dto.TripSummaryResponse;
import com.example.backend.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips")

public class TripController {
    private final TripService tripService;

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
}
