package com.arcadia.station.controller;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.SessionCreateRequest;
import com.arcadia.station.dto.response.PlayerCaseView;
import com.arcadia.station.dto.response.SessionCreateResponse;
import com.arcadia.station.dto.response.SessionStatusResponse;
import com.arcadia.station.service.GameSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class GameSessionController {

    private final GameSessionService gameSessionService;

    public GameSessionController(GameSessionService gameSessionService) {
        this.gameSessionService = gameSessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SessionCreateResponse>> createSession(
            @RequestBody(required = false) SessionCreateRequest request) {
        String seed = request != null ? request.seed() : null;
        SessionCreateResponse response = gameSessionService.createSession(seed);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<SessionStatusResponse> getStatus(@PathVariable("id") String id) {
        return ApiResponse.success(gameSessionService.getStatus(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlayerCaseView> getPlayerView(@PathVariable("id") String id) {
        return ApiResponse.success(gameSessionService.getPlayerView(id));
    }
}
