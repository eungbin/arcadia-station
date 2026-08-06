package com.arcadia.station.dto.response;

import java.util.List;
import java.util.Map;

public record DeductionResult(
    String verdict,
    boolean culpritCorrect,
    Map<String, String> roleResults,
    int remainingAttempts,
    String feedback,
    Map<String, String> exclusionResults,
    List<MissingLogicItem> missingLogic
) {}
