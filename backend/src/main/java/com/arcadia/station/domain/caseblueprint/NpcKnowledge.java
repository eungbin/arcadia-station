package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record NpcKnowledge(
    String characterId,
    List<String> knownFactIds,
    List<String> initialClaimFactIds,
    List<String> concealedFactIds,
    List<RevealPolicy> revealPolicies,
    List<String> allowedLieFactIds,
    List<String> recommendedQuestionTopics
) {}
