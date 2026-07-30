package com.arcadia.station.service;

import com.arcadia.station.client.AiSessionLostException;
import com.arcadia.station.client.AiTurnFailedException;
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
        if (question == null || question.isBlank() || presentedClueIds == null) {
            // 5.2/5.4절: 질문은 공백 불가, presentedClueIds는 필수(빈 배열은 허용)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

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

        // AI 서버는 NPC 턴 이력을 자체적으로 누적하지 않으므로(AI 서버 회신 3.2절), 이번 턴에 새로
        // 제시한 단서뿐 아니라 이 세션·이 NPC에게 지금까지 제시했던 단서 전체를 합쳐서 보낸다.
        Set<String> cumulativePresentedClueIds = new HashSet<>(inventory.getPresentedClueIds(characterId));
        cumulativePresentedClueIds.addAll(presentedClueIds);

        NpcTurnResult result;
        try {
            result = interrogationClient.ask(
                    session.getAiCaseRequestId(), characterId, question, List.copyOf(cumulativePresentedClueIds));
        } catch (AiSessionLostException e) {
            return handleAiSessionLost(session, knowledge);
        } catch (AiTurnFailedException e) {
            log.warn("AI 서버 심문 호출 실패, 안전 응답으로 대체: session={}, characterId={}", sessionId, characterId, e);
            return safeFallbackResponse(knowledge);
        }

        List<String> safeRevealedFacts =
                whitelistRevealedFacts(knowledge, cumulativePresentedClueIds, result.revealedFactIds());

        inventory.recordPresentedClues(characterId, presentedClueIds);
        inventory.getRevealedFactIds().addAll(safeRevealedFacts);
        evidenceInventoryRepository.save(inventory);

        List<RecommendedQuestionView> recommended = result.recommendedQuestions().stream()
                .map(q -> new RecommendedQuestionView(q.topicId(), q.label()))
                .toList();
        return new NpcTurnResponse(result.dialogue(), result.emotion(), safeRevealedFacts, recommended);
    }

    // revealedFactIds ⊆ (initialClaimFactIds ∪ 지금까지 누적 제시로 조건이 충족된 revealPolicy의 factId) 인지
    // 검증하고, 하나라도 벗어나면 5.3절대로 전체를 빈 배열로 덮어쓴다.
    private List<String> whitelistRevealedFacts(
            NpcKnowledge knowledge, Set<String> presentedClueIds, List<String> claimedRevealedFactIds) {
        Set<String> allowedFacts = new HashSet<>(knowledge.initialClaimFactIds());
        knowledge.revealPolicies().stream()
                .filter(policy -> presentedClueIds.containsAll(policy.requiredPresentedClueIds()))
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
        return safeFallbackResponse(knowledge);
    }

    // 13장: "NPC/RAG 호출이 실패(404 포함)하면 게임 진행 자체를 막지 않는다" — 404 외의 실패(4xx/5xx/타임아웃)도
    // 동일한 고정 안전 응답으로 대체한다. AI_SESSION_LOST 플래그는 세션 유실(404)에만 해당하므로 여기서는 남기지 않는다.
    private NpcTurnResponse safeFallbackResponse(NpcKnowledge knowledge) {
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
