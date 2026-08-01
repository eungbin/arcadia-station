package com.arcadia.station.ai.npc;

import java.util.List;

public record NpcTurnContext(
        String sessionId,
        String characterId,
        String displayName,
        String occupation,
        List<String> personalityTraits,
        String question,
        List<String> presentedClueIds,
        List<AllowedFact> allowedFacts,
        List<String> revealableFactIds,
        List<QuestionCandidate> questionCandidates
) {
    public record AllowedFact(String factId, String statement, boolean truthValue) {}

    public record QuestionCandidate(String topicId, String label) {}
}
