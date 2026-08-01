package com.arcadia.station.controller;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.InterrogationTurnRequest;
import com.arcadia.station.dto.response.NpcTurnResponse;
import com.arcadia.station.service.InterrogationProxyService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{id}/interrogations/{characterId}/turns")
public class InterrogationController {

    private final InterrogationProxyService interrogationProxyService;

    public InterrogationController(InterrogationProxyService interrogationProxyService) {
        this.interrogationProxyService = interrogationProxyService;
    }

    @PostMapping
    public ApiResponse<NpcTurnResponse> ask(
            @PathVariable("id") String sessionId,
            @PathVariable("characterId") String characterId,
            @RequestBody InterrogationTurnRequest request) {
        return ApiResponse.success(interrogationProxyService.ask(
                sessionId, characterId, request.question(), request.presentedClueIds()));
    }
}
