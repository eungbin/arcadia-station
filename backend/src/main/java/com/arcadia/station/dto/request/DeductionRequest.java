package com.arcadia.station.dto.request;

import java.util.Map;

public record DeductionRequest(
        String culpritId, Map<String, String> evidenceByRole, Map<String, String> exclusionsByCharacter) {}
