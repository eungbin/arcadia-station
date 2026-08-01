package com.arcadia.station.client.dto;

public record CaseGenerationStatus(
    String aiCaseRequestId,
    String status,
    GenerationResult generation,
    String errorCode
) {}
