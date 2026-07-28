package com.arcadia.station.controller;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.AssistantQueryRequest;
import com.arcadia.station.dto.response.AssistantQueryResponse;
import com.arcadia.station.service.AssistantProxyService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{id}/assistant/queries")
public class AssistantController {

    private final AssistantProxyService assistantProxyService;

    public AssistantController(AssistantProxyService assistantProxyService) {
        this.assistantProxyService = assistantProxyService;
    }

    @PostMapping
    public ApiResponse<AssistantQueryResponse> query(
            @PathVariable("id") String sessionId, @RequestBody AssistantQueryRequest request) {
        return ApiResponse.success(assistantProxyService.query(sessionId, request.question()));
    }
}
