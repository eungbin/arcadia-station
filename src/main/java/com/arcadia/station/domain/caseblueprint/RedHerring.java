package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record RedHerring(
    String redHerringId,
    String suspectId,
    String presentation,
    List<String> resolutionFactIds,
    boolean mustBeResolvable
) {}
