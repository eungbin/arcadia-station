package com.arcadia.station.service;

import com.arcadia.station.client.AiSessionLostException;
import com.arcadia.station.client.InterrogationClient;
import com.arcadia.station.client.dto.NpcTurnResult;
import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.NpcKnowledge;
import com.arcadia.station.dto.response.NpcTurnResponse;
import com.arcadia.station.dto.response.RecommendedQuestionView;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 5장 NPC 심문 프록시. AI 서버 응답의 revealedFactIds를 그대로 믿지 않고
 * npcKnowledge 기준으로 화이트리스트 재검증한다(5.3절).
 */
@Service
@Transactional
public class InterrogationProxyService {

    private static final Logger log = LoggerFactory.getLogger(InterrogationProxyService.class);

    private final GameSessionRepository gameSessionRepository;
    private final EvidenceInventoryRepository evidenceInventoryRepository;
    private final InterrogationClient interrogationClient;
    private final ObjectMapper objectMapper;

    public InterrogationProxyService(
            GameSessionRepository gameSessionRepository,
            EvidenceInventoryRepository evidenceInventoryRepository,
            InterrogationClient interrogationClient,
            ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.evidenceInventoryRepository = evidenceInventoryRepository;
        this.interrogationClient = interrogationClient;
        this.objectMapper = objectMapper;
    }

    public NpcTurnResponse ask(String sessionId, String characterId, String question, List<String> presentedClueIds) {
        GameSession session = findSessionOrThrow(sessionId);
        requireReady(session);

        EvidenceInventory inventory = evidenceInventoryRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!inventory.getDiscoveredClueIds().containsAll(presentedClueIds)) {
            // 5.1절: 아직 발견하지 않은 단서를 제시하면 안 된다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        CaseBlueprint blueprint = objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
        NpcKnowledge knowledge = blueprint.npcKnowledge().stream()
                .filter(k -> k.characterId().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));

        NpcTurnResult result;
        try {
            result = interrogationClient.ask(sessionId, characterId, question, presentedClueIds);
        } catch (AiSessionLostException e) {
            return handleAiSessionLost(session, knowledge);
        }

        List<String> safeRevealedFacts = whitelistRevealedFacts(knowledge, presentedClueIds, result.revealedFactIds());

        inventory.getPresentedClueIdsByCharacter().addAll(presentedClueIds);
        inventory.getRevealedFactIds().addAll(safeRevealedFacts);
        evidenceInventoryRepository.save(inventory);

        List<RecommendedQuestionView> recommended = result.recommendedQuestions().stream()
                .map(q -> new RecommendedQuestionView(q.topicId(), q.label()))
                .toList();
        return new NpcTurnResponse(result.dialogue(), result.emotion(), safeRevealedFacts, recommended);
    }

    // revealedFactIds ⊆ (initialClaimFactIds ∪ 이번 턴 제시로 조건이 충족된 revealPolicy의 factId) 인지 검증하고,
    // 하나라도 벗어나면 5.3절대로 전체를 빈 배열로 덮어쓴다.
    private List<String> whitelistRevealedFacts(
            NpcKnowledge knowledge, List<String> presentedClueIds, List<String> claimedRevealedFactIds) {
        Set<String> presented = Set.copyOf(presentedClueIds);
        Set<String> allowedFacts = new HashSet<>(knowledge.initialClaimFactIds());
        knowledge.revealPolicies().stream()
                .filter(policy -> presented.containsAll(policy.requiredPresentedClueIds()))
                .forEach(policy -> allowedFacts.add(policy.factId()));

        if (allowedFacts.containsAll(claimedRevealedFactIds)) {
            return claimedRevealedFactIds;
        }
        return List.of();
    }

    // 13장: AI 서버가 세션 메모리를 잃어 404 SESSION_NOT_FOUND를 반환한 경우, 게임을 중단시키지 않고
    // 고정 안전 응답 + 백엔드가 미리 알고 있는 추천 질문 후보로 대체한다.
    private NpcTurnResponse handleAiSessionLost(GameSession session, NpcKnowledge knowledge) {
        log.warn("AI_SESSION_LOST: session={}, characterId={}", session.getSessionId(), knowledge.characterId());
        session.setAiSessionLost(true);
        gameSessionRepository.save(session);

        List<RecommendedQuestionView> fallbackQuestions = knowledge.recommendedQuestionTopics().stream()
                .limit(2)
                .map(topic -> new RecommendedQuestionView("TOPIC-" + topic.hashCode(), topic))
                .toList();
        return new NpcTurnResponse("지금은 대답할 수 없습니다. 잠시 후 다시 시도해주세요.", "CALM", List.of(), fallbackQuestions);
    }

    private void requireReady(GameSession session) {
        if (session.getCaseBlueprintJson() == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_READY);
        }
        if (session.getState() == SessionState.COMPLETED || session.getState() == SessionState.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
    }

    private GameSession findSessionOrThrow(String sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
