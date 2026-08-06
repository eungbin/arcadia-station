package com.arcadia.station.ai.npc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NpcResponseGuard {

    public boolean isAllowed(NpcTurnContext context, NpcTurnResponse response) {
        Set<String> revealable = Set.copyOf(context.revealableFactIds());
        if (!revealable.containsAll(response.revealedFactIds())) {
            return false;
        }
        Map<String, String> labelsByTopicId = context.questionCandidates().stream()
                .collect(Collectors.toMap(
                        NpcTurnContext.QuestionCandidate::topicId,
                        NpcTurnContext.QuestionCandidate::label,
                        (first, ignored) -> first
                ));
        if (response.recommendedQuestions().size() != 2) {
            return false;
        }
        return response.recommendedQuestions().stream()
                .allMatch(question -> question.label() != null
                        && question.label().equals(labelsByTopicId.get(question.topicId())))
                && response.recommendedQuestions().stream()
                        .map(NpcTurnResponse.RecommendedQuestion::topicId)
                        .collect(Collectors.toSet())
                        .size() == 2;
    }

    public NpcTurnResponse safeFallback(NpcTurnContext context) {
        List<NpcTurnResponse.RecommendedQuestion> questions = context.questionCandidates().stream()
                .limit(2)
                .map(candidate -> new NpcTurnResponse.RecommendedQuestion(
                        candidate.topicId(),
                        candidate.label()
                ))
                .toList();
        return new NpcTurnResponse(
                "확인된 기록의 범위 안에서만 답하겠습니다. 지금 제시한 자료만으로는 더 말하기 어렵습니다.",
                NpcTurnResponse.Emotion.DEFENSIVE,
                List.of(),
                questions
        );
    }
}
