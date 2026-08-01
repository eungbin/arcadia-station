package com.arcadia.station.domain.caseblueprint;

import java.util.List;
import java.util.Map;

public record Solution(
    String culpritId,
    Map<String, List<String>> requiredEvidenceByRole,
    Map<String, List<String>> acceptedAlternativesByRole,
    List<NonCulpritExclusion> nonCulpritExclusions
) {}
