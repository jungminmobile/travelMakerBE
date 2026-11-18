package com.example.backend.place.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.place.dto.PlaceSearchResponse;
import com.example.backend.place.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/places")
public class PlaceController {
    private final PlaceService placeService;

    @GetMapping("/search")
    public ApiResponse<List<PlaceSearchResponse>> search(@RequestParam String keyword){
        return ApiResponse.ok(placeService.searchPlaces(keyword));
    }
}
