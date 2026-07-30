package com.arcadia.station.service;

import com.arcadia.station.client.CaseGenerationClient;
import com.arcadia.station.client.dto.CaseGenerationAck;
import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.AcquisitionType;
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
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class GameSessionService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionService.class);

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
        session.setSeed(seed);
        gameSessionRepository.save(session);
        evidenceInventoryRepository.save(new EvidenceInventory(sessionId));

        // 최초 시도가 AI 서버와 통신 자체에 실패해도(4.3/4.5절) 이미 저장한 세션 행을 롤백시키지 않는다.
        // CREATING 상태로 남겨두면 CaseGenerationPollingWorker가 이어서 재시도 정책을 적용한다.
        try {
            CaseGenerationAck ack = caseGenerationClient.requestCase(aiCaseRequestId, seed);
            session.setState(SessionState.VALIDATING);

            CaseGenerationStatus status = caseGenerationClient.pollStatus(ack.aiCaseRequestId());
            session.setLastPolledAt(Instant.now());
            if ("READY".equals(status.status())) {
                GenerationResultApplier.apply(session, status.generation());
            }
            gameSessionRepository.save(session);
        } catch (Exception e) {
            log.warn("사건 생성 최초 시도가 AI 서버와 통신에 실패함, 폴링 워커가 재시도를 이어감: session={}", sessionId, e);
        }

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
            return new PlayerCaseView(sessionId, session.getState().name(), null, null, List.of(), List.of(), List.of());
        }

        CaseBlueprint blueprint = objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
        List<PlayerClueView> discoveredClues = blueprint.clues().stream()
                .filter(clue -> inventory.getDiscoveredClueIds().contains(clue.clueId()))
                .map(clue -> new PlayerClueView(clue.clueId(), clue.title(), clue.clueType(), clue.playerText()))
                .toList();
        // 심문 UI에 노출할 용의자 전원 목록. alibis는 사건에 등장하는 모든 용의자(범인 포함)를
        // 빠짐없이 담고 있어 npcKnowledge(일부만 생성될 수 있음)보다 완전한 소스다.
        List<String> suspectCharacterIds = blueprint.alibis().stream()
                .map(alibi -> alibi.characterId())
                .distinct()
                .toList();
        // 탐사 UI에 노출할 장소 목록. EXPLORE 단서의 locationId는 사건마다 다르게 생성되므로
        // 프론트가 미리 알 수 없다 — characterId와 같은 이유로 여기서 동적으로 내려준다.
        List<String> exploreLocationIds = blueprint.clues().stream()
                .filter(clue -> clue.acquisition().type() == AcquisitionType.EXPLORE)
                .map(clue -> clue.acquisition().locationId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return new PlayerCaseView(
                sessionId, session.getState().name(), blueprint.title(), blueprint.briefing(),
                discoveredClues, suspectCharacterIds, exploreLocationIds);
    }

    private GameSession findSessionOrThrow(String sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
