package com.arcadia.station.dto.response;

import java.util.List;

public record NpcTurnResponse(
    String dialogue,
    String emotion,
    List<String> revealedFactIds,
    List<RecommendedQuestionView> recommendedQuestions
) {}
