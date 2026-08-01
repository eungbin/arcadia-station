package com.arcadia.station.domain.caseblueprint;

import java.util.List;
import java.util.Map;

public record EvidenceRecord(
    String recordId,
    String recordType,
    String timestamp,
    String title,
    String body,
    Map<String, String> metadata,
    List<String> revealsClueIds,
    List<String> searchTerms,
    RecordVisibility visibility
) {}
