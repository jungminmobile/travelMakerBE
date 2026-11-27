package com.travelmaker.backend.itinerary.controller;

import com.travelmaker.backend.auth.security.AuthenticatedUser;
import com.travelmaker.backend.common.ApiResponse;
import com.travelmaker.backend.itinerary.dto.ActivateItineraryResponse;
import com.travelmaker.backend.itinerary.dto.ItineraryDetailResponse;
import com.travelmaker.backend.itinerary.dto.ItinerarySummaryResponse;
import com.travelmaker.backend.itinerary.dto.UpdateItineraryRequest;
import com.travelmaker.backend.itinerary.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/itineraries")
public class ItineraryController {
    private final ItineraryService itineraryService;

    @GetMapping
    public ApiResponse<List<ItinerarySummaryResponse>> getItineraries(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal AuthenticatedUser currentUser){
        return ApiResponse.ok(itineraryService.getItineraries( tripId,currentUser.userId()));
    }

    @GetMapping("/{itineraryId}")
    public ApiResponse<ItineraryDetailResponse> getItinerary(
            @PathVariable UUID tripId,
            @PathVariable UUID itineraryId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(itineraryService.getItinerary(tripId, itineraryId, currentUser.userId()));
    }

    @PatchMapping("/{itineraryId}")
    public ApiResponse<ItinerarySummaryResponse> updateItinerary(
            @PathVariable UUID tripId,
            @PathVariable UUID itineraryId,
            @RequestBody UpdateItineraryRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(itineraryService.updateItinerary(tripId, itineraryId, currentUser.userId(), request));
    }

    @PostMapping("/{itineraryId}/activate")
    public ApiResponse<ActivateItineraryResponse> activate(
            @PathVariable UUID tripId,
            @PathVariable UUID itineraryId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(itineraryService.activate(tripId, itineraryId, currentUser.userId()));
    }
}
