package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record Acquisition(
    AcquisitionType type,
    String locationId,
    String characterId,
    List<String> requiredClueIds,
    List<String> queryTopics
) {}
