package com.arcadia.station.domain.caseblueprint;

public record Method(
    String fictionalSummary,
    CaseAction setupAction,
    CaseAction triggerAction,
    String victimCondition
) {}
