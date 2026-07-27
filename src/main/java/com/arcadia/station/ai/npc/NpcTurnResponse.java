package com.arcadia.station.ai.npc;

import java.util.List;

public record NpcTurnResponse(
        String dialogue,
        Emotion emotion,
        List<String> revealedFactIds,
        List<RecommendedQuestion> recommendedQuestions
) {
    public enum Emotion {
        CALM,
        DEFENSIVE,
        ANXIOUS,
        ANGRY,
        EVASIVE
    }

    public record RecommendedQuestion(String topicId, String label) {}
}
