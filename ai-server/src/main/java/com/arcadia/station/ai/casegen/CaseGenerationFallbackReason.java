package com.arcadia.station.ai.casegen;

public enum CaseGenerationFallbackReason {
    NONE,
    AI_DISABLED,
    OFFLINE_MODE,
    MISSING_API_KEY,
    QUOTA_EXCEEDED,
    GENERATION_FAILURE,
    VALIDATION_EXHAUSTED
}
