package com.arcadia.station.controller;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.ExploreRequest;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.service.ExplorationService;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{id}/explore")
public class ExplorationController {

    private final ExplorationService explorationService;

    public ExplorationController(ExplorationService explorationService) {
        this.explorationService = explorationService;
    }

    @PostMapping
    public ApiResponse<List<PlayerClueView>> explore(
            @PathVariable("id") String sessionId, @RequestBody ExploreRequest request) {
        return ApiResponse.success(explorationService.explore(sessionId, request.locationId()));
    }
}
