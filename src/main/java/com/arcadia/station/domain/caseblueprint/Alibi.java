package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record Alibi(
    String characterId,
    String initialClaim,
    String actualWhereabouts,
    List<String> supportingFactIds,
    List<String> contradictingFactIds
) {}
