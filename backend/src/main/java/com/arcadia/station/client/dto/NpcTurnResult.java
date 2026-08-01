package com.arcadia.station.client.dto;

import java.util.List;

public record NpcTurnResult(
    String dialogue,
    String emotion,
    List<String> revealedFactIds,
    List<RecommendedQuestion> recommendedQuestions
) {}
