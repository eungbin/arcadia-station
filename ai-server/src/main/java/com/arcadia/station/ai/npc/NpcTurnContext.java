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
        List<ConversationTurn> conversationHistory,
        List<AllowedFact> allowedFacts,
        List<String> revealableFactIds,
        List<QuestionCandidate> questionCandidates
) {
    public record AllowedFact(String factId, String statement, boolean truthValue) {}

    /** 이미 검증된 최근 문답만 모델에 전달하는 플레이어별 NPC 메모리 뷰. */
    public record ConversationTurn(
            String question,
            String dialogue,
            String emotion,
            List<String> presentedClueIds,
            List<String> revealedFactIds
    ) {}

    public record QuestionCandidate(String topicId, String label) {}
}
