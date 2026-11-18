package com.example.backend.itinerary.controller;

import com.example.backend.auth.security.AuthenticatedUser;
import com.example.backend.common.ApiResponse;
import com.example.backend.itinerary.dto.ItinerarySummaryResponse;
import com.example.backend.itinerary.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
