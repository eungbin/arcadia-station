package com.arcadia.station.service;

import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.AcquisitionType;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.Clue;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 8장 탐사 처리: EXPLORE 단서 해금 + CONNECT 단서 연쇄 재검사.
 */
@Service
@Transactional
public class ClueUnlockServiceImpl implements ClueUnlockService {

    private static final Logger log = LoggerFactory.getLogger(ClueUnlockServiceImpl.class);

    private final GameSessionRepository gameSessionRepository;
    private final EvidenceInventoryRepository evidenceInventoryRepository;
    private final ObjectMapper objectMapper;

    public ClueUnlockServiceImpl(
            GameSessionRepository gameSessionRepository,
            EvidenceInventoryRepository evidenceInventoryRepository,
            ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.evidenceInventoryRepository = evidenceInventoryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Clue> exploreLocation(String sessionId, String locationId) {
        GameSession session = findSessionOrThrow(sessionId);
        enterInvestigationIfBriefing(session);

        EvidenceInventory inventory = findInventoryOrThrow(sessionId);
        CaseBlueprint blueprint = readBlueprint(session);

        List<Clue> newlyUnlocked = new ArrayList<>();
        for (Clue clue : blueprint.clues()) {
            if (clue.acquisition().type() == AcquisitionType.EXPLORE
                    && locationId.equals(clue.acquisition().locationId())
                    && isUnlockable(clue, inventory)) {
                unlock(clue, inventory);
                newlyUnlocked.add(clue);
            }
        }

        newlyUnlocked.addAll(resolveConnectClues(blueprint, inventory));

        gameSessionRepository.save(session);
        evidenceInventoryRepository.save(inventory);
        return newlyUnlocked;
    }

    @Override
    public List<Clue> unlockRagClues(String sessionId, List<String> candidateClueIds) {
        GameSession session = findSessionOrThrow(sessionId);
        EvidenceInventory inventory = findInventoryOrThrow(sessionId);
        CaseBlueprint blueprint = readBlueprint(session);

        List<Clue> newlyUnlocked = new ArrayList<>();
        for (String clueId : candidateClueIds) {
            Clue clue = findClueById(blueprint, clueId);
            if (clue == null || clue.acquisition().type() != AcquisitionType.RAG_QUERY) {
                log.warn("RAG 응답이 등록되지 않았거나 RAG_QUERY 타입이 아닌 clueId를 제안함: session={}, clueId={}", sessionId, clueId);
                continue;
            }
            if (isUnlockable(clue, inventory)) {
                unlock(clue, inventory);
                newlyUnlocked.add(clue);
            }
        }

        newlyUnlocked.addAll(resolveConnectClues(blueprint, inventory));

        evidenceInventoryRepository.save(inventory);
        return newlyUnlocked;
    }

    @Override
    public List<Clue> resolveConnectClues(String sessionId) {
        GameSession session = findSessionOrThrow(sessionId);
        EvidenceInventory inventory = findInventoryOrThrow(sessionId);
        CaseBlueprint blueprint = readBlueprint(session);

        List<Clue> unlocked = resolveConnectClues(blueprint, inventory);
        evidenceInventoryRepository.save(inventory);
        return unlocked;
    }

    // CONNECT 타입 단서는 해금 이벤트가 있을 때마다 "지금 보유한 조합으로 새로 열리는 단서가 있는지" 재검사한다(8장 5단계).
    private List<Clue> resolveConnectClues(CaseBlueprint blueprint, EvidenceInventory inventory) {
        List<Clue> unlockedThisRound = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Clue clue : blueprint.clues()) {
                if (clue.acquisition().type() == AcquisitionType.CONNECT && isUnlockable(clue, inventory)) {
                    unlock(clue, inventory);
                    unlockedThisRound.add(clue);
                    changed = true;
                }
            }
        }
        return unlockedThisRound;
    }

    private boolean isUnlockable(Clue clue, EvidenceInventory inventory) {
        return !inventory.getDiscoveredClueIds().contains(clue.clueId())
                && inventory.getDiscoveredClueIds().containsAll(clue.acquisition().requiredClueIds());
    }

    private void unlock(Clue clue, EvidenceInventory inventory) {
        inventory.getDiscoveredClueIds().add(clue.clueId());
        inventory.getRevealedFactIds().addAll(clue.revealsFactIds());
    }

    // 스펙에 BRIEFING -> INVESTIGATION 전환 트리거가 되는 별도 엔드포인트가 없어,
    // 플레이어가 처음 탐사를 시도하는 시점에 자동으로 INVESTIGATION으로 승격시킨다.
    private void enterInvestigationIfBriefing(GameSession session) {
        if (session.getState() == SessionState.BRIEFING) {
            session.setState(SessionState.INVESTIGATION);
        } else if (session.getState() != SessionState.INVESTIGATION) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
    }

    private Clue findClueById(CaseBlueprint blueprint, String clueId) {
        return blueprint.clues().stream()
                .filter(clue -> clue.clueId().equals(clueId))
                .findFirst()
                .orElse(null);
    }

    private CaseBlueprint readBlueprint(GameSession session) {
        return objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
    }

    private GameSession findSessionOrThrow(String sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    private EvidenceInventory findInventoryOrThrow(String sessionId) {
        return evidenceInventoryRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
