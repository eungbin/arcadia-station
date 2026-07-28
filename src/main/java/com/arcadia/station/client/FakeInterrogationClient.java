package com.arcadia.station.client;

import com.arcadia.station.client.dto.NpcTurnResult;
import com.arcadia.station.client.dto.RecommendedQuestion;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.NpcKnowledge;
import com.arcadia.station.domain.caseblueprint.RevealPolicy;
import com.arcadia.station.repository.GameSessionRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 AI 서버 없이 5장 계약을 흉내내는 Fake 구현체. 세션의 CaseBlueprint(npcKnowledge)를 그대로 읽어
 * revealPolicies 조건을 만족하는 사실만 공개하는, "정상적으로 동작하는" AI 서버를 흉내낸다.
 * 백엔드의 화이트리스트 재검증 로직(InterrogationProxyService)은 이 Fake의 선의와 무관하게 항상 동작해야 한다.
 */
@Component
public class FakeInterrogationClient implements InterrogationClient {

    private final GameSessionRepository gameSessionRepository;
    private final ObjectMapper objectMapper;

    public FakeInterrogationClient(GameSessionRepository gameSessionRepository, ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public NpcTurnResult ask(String sessionId, String characterId, String question, List<String> presentedClueIds) {
        CaseBlueprint blueprint = readBlueprint(sessionId);
        Set<String> presented = Set.copyOf(presentedClueIds);

        NpcKnowledge knowledge = blueprint.npcKnowledge().stream()
                .filter(k -> k.characterId().equals(characterId))
                .findFirst()
                .orElse(null);

        if (knowledge == null) {
            return new NpcTurnResult("잘 모르는 사람이군요.", "CALM", List.of(), List.of());
        }

        List<String> revealed = knowledge.revealPolicies().stream()
                .filter(policy -> presented.containsAll(policy.requiredPresentedClueIds()))
                .map(RevealPolicy::factId)
                .toList();

        String dialogue = revealed.isEmpty()
                ? "그 부분은 말씀드리기 어렵네요."
                : "그 증거를 보여주신다면... 인정할 수밖에 없겠네요.";
        String emotion = revealed.isEmpty() ? "EVASIVE" : "DEFENSIVE";

        List<RecommendedQuestion> recommended = knowledge.recommendedQuestionTopics().stream()
                .limit(2)
                .map(topic -> new RecommendedQuestion("TOPIC-" + topic.hashCode(), topic))
                .toList();

        return new NpcTurnResult(dialogue, emotion, revealed, recommended);
    }

    private CaseBlueprint readBlueprint(String sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Unknown session: " + sessionId));
        return objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
    }
}
