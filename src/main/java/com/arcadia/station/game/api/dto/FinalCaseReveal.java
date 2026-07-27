package com.arcadia.station.game.api.dto;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import java.util.List;

public record FinalCaseReveal(
        String sessionId,
        String title,
        String culpritId,
        String truthSummary,
        CaseBlueprint.Method method,
        List<CaseBlueprint.TimelineEvent> timeline,
        List<CaseBlueprint.NonCulpritExclusion> nonCulpritExclusions
) {}
