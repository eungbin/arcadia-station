package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record NonCulpritExclusion(String characterId, List<String> excludedByClueIds, String reason) {}
