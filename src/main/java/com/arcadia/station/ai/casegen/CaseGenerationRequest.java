package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.List;

public record CaseGenerationRequest(
        String sessionId,
        String seed,
        WorldTemplate world,
        MysteryRuleTemplate rules,
        List<ValidationIssue> previousIssues
) {}
