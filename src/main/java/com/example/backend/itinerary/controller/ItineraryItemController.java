package com.example.backend.itinerary.controller;

import com.example.backend.auth.security.AuthenticatedUser;
import com.example.backend.common.ApiResponse;
import com.example.backend.itinerary.dto.CreateItineraryItemRequest;
import com.example.backend.itinerary.dto.CreateItineraryItemResponse;
import com.example.backend.itinerary.dto.DeleteItineraryItemResponse;
import com.example.backend.itinerary.dto.UpdateItineraryItemRequest;
import com.example.backend.itinerary.dto.UpdateItineraryItemResponse;
import com.example.backend.itinerary.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}")

public class ItineraryItemController {
    private final ItineraryService itineraryService;

    @PatchMapping("/itinerary-items/{itemId}")
    public ApiResponse<UpdateItineraryItemResponse> updateItineraryItem(
            @PathVariable UUID tripId,
            @PathVariable UUID itemId,
            @RequestBody UpdateItineraryItemRequest itineraryItemRequest,
            @AuthenticationPrincipal AuthenticatedUser currentuser
    ){
        return ApiResponse.ok(itineraryService.updateItem(tripId,itemId,currentuser.userId(),itineraryItemRequest));
    }

    @PostMapping("/itineraries/{itineraryId}/days/{dayId}/items")
    public ResponseEntity<ApiResponse<CreateItineraryItemResponse>> addItineraryItem(
            @PathVariable UUID tripId,
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @RequestBody CreateItineraryItemRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        CreateItineraryItemResponse response =
                itineraryService.addItem(tripId, itineraryId, dayId, currentUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @DeleteMapping("/itinerary-items/{itemId}")
    public ApiResponse<DeleteItineraryItemResponse> deleteItineraryItem(
            @PathVariable UUID tripId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        itineraryService.deleteItem(tripId, itemId, currentUser.userId());
        return ApiResponse.ok(DeleteItineraryItemResponse.deleted());
    }
}
