package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record CaseAction(
    String actorId,
    String locationId,
    String systemId,
    String operation,
    List<String> requiredCapabilityIds
) {}
