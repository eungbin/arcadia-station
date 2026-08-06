package com.arcadia.station.controller;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.DeductionRequest;
import com.arcadia.station.dto.response.DeductionResult;
import com.arcadia.station.dto.response.FinalCaseReveal;
import com.arcadia.station.service.DeductionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{id}")
public class DeductionController {

    private final DeductionService deductionService;

    public DeductionController(DeductionService deductionService) {
        this.deductionService = deductionService;
    }

    @PostMapping("/deductions")
    public ApiResponse<DeductionResult> submit(
            @PathVariable("id") String sessionId, @RequestBody DeductionRequest request) {
        return ApiResponse.success(deductionService.submit(
                sessionId, request.culpritId(), request.evidenceByRole(), request.exclusionsByCharacter()));
    }

    @GetMapping("/result")
    public ApiResponse<FinalCaseReveal> getResult(@PathVariable("id") String sessionId) {
        return ApiResponse.success(deductionService.getFinalReveal(sessionId));
    }
}
