package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record Clue(
    String clueId,
    String title,
    ClueType clueType,
    boolean isCore,
    List<EvidenceRole> solutionRoles,
    ClueSource source,
    Acquisition acquisition,
    List<String> revealsFactIds,
    String playerText,
    List<SuspectEffect> suspectEffects
) {}
