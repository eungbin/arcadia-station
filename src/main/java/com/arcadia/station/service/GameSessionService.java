package com.arcadia.station.service;

import com.arcadia.station.client.CaseGenerationClient;
import com.arcadia.station.client.dto.CaseGenerationAck;
import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.client.dto.GenerationResult;
import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.dto.response.PlayerCaseView;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.dto.response.SessionCreateResponse;
import com.arcadia.station.dto.response.SessionStatusResponse;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final EvidenceInventoryRepository evidenceInventoryRepository;
    private final CaseGenerationClient caseGenerationClient;
    private final ObjectMapper objectMapper;

    public GameSessionService(
            GameSessionRepository gameSessionRepository,
            EvidenceInventoryRepository evidenceInventoryRepository,
            CaseGenerationClient caseGenerationClient,
            ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.evidenceInventoryRepository = evidenceInventoryRepository;
        this.caseGenerationClient = caseGenerationClient;
        this.objectMapper = objectMapper;
    }

    public SessionCreateResponse createSession(String seed) {
        String sessionId = "game_" + UUID.randomUUID().toString().replace("-", "");
        String aiCaseRequestId = "req_" + UUID.randomUUID().toString().replace("-", "");

        GameSession session = new GameSession(sessionId, aiCaseRequestId, Instant.now());
        gameSessionRepository.save(session);
        evidenceInventoryRepository.save(new EvidenceInventory(sessionId));

        CaseGenerationAck ack = caseGenerationClient.requestCase(aiCaseRequestId, seed);
        session.setState(SessionState.VALIDATING);

        CaseGenerationStatus status = caseGenerationClient.pollStatus(ack.aiCaseRequestId());
        if ("READY".equals(status.status())) {
            applyReady(session, status.generation());
        }

        gameSessionRepository.save(session);
        return new SessionCreateResponse(sessionId, session.getState().name());
    }

    @Transactional(readOnly = true)
    public SessionStatusResponse getStatus(String sessionId) {
        GameSession session = findSessionOrThrow(sessionId);
        return new SessionStatusResponse(sessionId, session.getState().name());
    }

    @Transactional(readOnly = true)
    public PlayerCaseView getPlayerView(String sessionId) {
        GameSession session = findSessionOrThrow(sessionId);
        EvidenceInventory inventory = evidenceInventoryRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getCaseBlueprintJson() == null) {
            return new PlayerCaseView(sessionId, session.getState().name(), null, null, List.of());
        }

        CaseBlueprint blueprint = objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
        List<PlayerClueView> discoveredClues = blueprint.clues().stream()
                .filter(clue -> inventory.getDiscoveredClueIds().contains(clue.clueId()))
                .map(clue -> new PlayerClueView(clue.clueId(), clue.title(), clue.clueType(), clue.playerText()))
                .toList();

        return new PlayerCaseView(sessionId, session.getState().name(), blueprint.title(), blueprint.briefing(), discoveredClues);
    }

    private void applyReady(GameSession session, GenerationResult generation) {
        CaseBlueprint blueprint = generation.caseBlueprint();
        session.setBlueprintId(blueprint.blueprintId());
        session.setWorldTemplateId(blueprint.worldTemplate().id());
        session.setWorldTemplateVersion(blueprint.worldTemplate().version());
        session.setRuleTemplateId(blueprint.ruleTemplate().id());
        session.setRuleTemplateVersion(blueprint.ruleTemplate().version());
        session.setBlueprintSha256(generation.blueprintSha256());
        session.setGenerationSource(generation.generationSource());
        session.setGenerationAttemptCount(generation.generationAttemptCount());
        session.setModel(generation.model());
        session.setPromptVersion(generation.promptVersion());
        session.setCaseBlueprintJson(generation.rawCaseBlueprintJson());
        session.setFrozenAt(generation.frozenAt());
        // 7.1절: CaseBlueprint 저장(동결) 직후 BRIEFING으로 전환
        session.setState(SessionState.BRIEFING);
    }

    private GameSession findSessionOrThrow(String sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
