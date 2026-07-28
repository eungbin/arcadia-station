package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record Fact(
    String factId,
    FactKind kind,
    String statement,
    boolean truthValue,
    List<String> subjectCharacterIds
) {}
