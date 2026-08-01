package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record RevealPolicy(
    String factId,
    List<String> requiredPresentedClueIds,
    ResponseMode responseMode
) {}
