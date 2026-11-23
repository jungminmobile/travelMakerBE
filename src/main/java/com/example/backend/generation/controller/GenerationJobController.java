package com.example.backend.generation.controller;

import com.example.backend.auth.security.AuthenticatedUser;
import com.example.backend.common.ApiResponse;
import com.example.backend.generation.dto.CreateGenerationJobRequest;
import com.example.backend.generation.dto.CreateGenerationJobResponse;
import com.example.backend.generation.dto.GenerationJobStatusResponse;
import com.example.backend.generation.dto.GenerationJobSummaryResponse;
import com.example.backend.generation.service.GenerationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * AI 일정 생성 비동기 작업 API 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/generation-jobs")
public class GenerationJobController {

    private final GenerationJobService generationJobService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateGenerationJobResponse>> createJob(
            @PathVariable UUID tripId,
            @RequestBody(required = false) CreateGenerationJobRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        CreateGenerationJobResponse response =
                generationJobService.createJob(tripId, currentUser.userId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<GenerationJobStatusResponse> getJob(
            @PathVariable UUID tripId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ApiResponse.ok(generationJobService.getJob(tripId, jobId, currentUser.userId()));
    }

    @GetMapping
    public ApiResponse<List<GenerationJobSummaryResponse>> getJobs(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ApiResponse.ok(generationJobService.getJobs(tripId, currentUser.userId()));
    }
}
