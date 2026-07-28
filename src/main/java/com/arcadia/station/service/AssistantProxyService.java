package com.arcadia.station.service;

import com.arcadia.station.client.AiSessionLostException;
import com.arcadia.station.client.AssistantClient;
import com.arcadia.station.client.dto.AssistantQueryResult;
import com.arcadia.station.client.dto.RagDiscoveredClueRef;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.Clue;
import com.arcadia.station.domain.caseblueprint.EvidenceRecord;
import com.arcadia.station.dto.response.AssistantQueryResponse;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.GameSessionRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 6장 RAG 사건기록 검색 프록시. citedRecordIds/newlyDiscoveredClues를 재검증한다(6.2절).
 */
@Service
@Transactional
public class AssistantProxyService {

    private static final Logger log = LoggerFactory.getLogger(AssistantProxyService.class);

    private final GameSessionRepository gameSessionRepository;
    private final AssistantClient assistantClient;
    private final ClueUnlockService clueUnlockService;
    private final ObjectMapper objectMapper;

    public AssistantProxyService(
            GameSessionRepository gameSessionRepository,
            AssistantClient assistantClient,
            ClueUnlockService clueUnlockService,
            ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.assistantClient = assistantClient;
        this.clueUnlockService = clueUnlockService;
        this.objectMapper = objectMapper;
    }

    public AssistantQueryResponse query(String sessionId, String question) {
        GameSession session = findSessionOrThrow(sessionId);
        requireReady(session);

        CaseBlueprint blueprint = objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
        AssistantQueryResult result;
        try {
            result = assistantClient.query(sessionId, question);
        } catch (AiSessionLostException e) {
            return handleAiSessionLost(session);
        }

        Set<String> knownRecordIds = blueprint.evidenceRecords().stream()
                .map(EvidenceRecord::recordId)
                .collect(Collectors.toSet());
        List<String> citedRecordIds = result.citedRecordIds().stream()
                .filter(recordId -> {
                    boolean known = knownRecordIds.contains(recordId);
                    if (!known) {
                        log.warn("RAG 응답이 존재하지 않는 recordId를 인용함: session={}, recordId={}", sessionId, recordId);
                    }
                    return known;
                })
                .toList();

        List<String> candidateClueIds = result.newlyDiscoveredClues().stream()
                .map(RagDiscoveredClueRef::clueId)
                .toList();
        List<Clue> unlocked = clueUnlockService.unlockRagClues(sessionId, candidateClueIds);
        List<PlayerClueView> newlyDiscoveredClues = unlocked.stream()
                .map(clue -> new PlayerClueView(clue.clueId(), clue.title(), clue.clueType(), clue.playerText()))
                .toList();

        return new AssistantQueryResponse(result.answer(), citedRecordIds, result.suggestedQueries(), newlyDiscoveredClues);
    }

    // 13장: AI 서버가 세션 메모리를 잃어 404 SESSION_NOT_FOUND를 반환한 경우의 우아한 실패 처리.
    private AssistantQueryResponse handleAiSessionLost(GameSession session) {
        log.warn("AI_SESSION_LOST: session={}", session.getSessionId());
        session.setAiSessionLost(true);
        gameSessionRepository.save(session);
        return new AssistantQueryResponse(
                "지금은 검색할 수 없습니다. 잠시 후 다시 시도해주세요.", List.of(), List.of(), List.of());
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
