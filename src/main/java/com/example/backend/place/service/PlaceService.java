package com.example.backend.place.service;

import com.example.backend.place.domain.Place;
import com.example.backend.place.dto.PlaceSearchResponse;
import com.example.backend.place.repository.PlaceRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {
    private final PlaceRepository placeRepository;
    public List<PlaceSearchResponse> searchPlaces (String keyword){
        List<Place> data = placeRepository.findByNameContainingIgnoreCase(keyword);
        List<PlaceSearchResponse> target = data.stream().map(PlaceSearchResponse::from).toList();
        return target;
    }
}
