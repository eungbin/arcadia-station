package com.arcadia.station.client.dto;

import java.util.List;

public record AssistantQueryResult(
    String answer,
    List<String> citedRecordIds,
    List<String> suggestedQueries,
    List<RagDiscoveredClueRef> newlyDiscoveredClues
) {}
