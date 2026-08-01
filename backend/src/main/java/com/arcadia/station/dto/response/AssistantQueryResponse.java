package com.arcadia.station.dto.response;

import java.util.List;

public record AssistantQueryResponse(
    String answer,
    List<String> citedRecordIds,
    List<String> suggestedQueries,
    List<PlayerClueView> newlyDiscoveredClues
) {}
