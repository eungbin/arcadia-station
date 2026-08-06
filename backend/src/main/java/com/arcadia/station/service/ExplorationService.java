package com.arcadia.station.service;

import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.Clue;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ExplorationService {

    private final ClueUnlockService clueUnlockService;
    private final GameSessionRepository gameSessionRepository;
    private final EvidenceInventoryRepository evidenceInventoryRepository;
    private final ObjectMapper objectMapper;

    public ExplorationService(
            ClueUnlockService clueUnlockService,
            GameSessionRepository gameSessionRepository,
            EvidenceInventoryRepository evidenceInventoryRepository,
            ObjectMapper objectMapper) {
        this.clueUnlockService = clueUnlockService;
        this.gameSessionRepository = gameSessionRepository;
        this.evidenceInventoryRepository = evidenceInventoryRepository;
        this.objectMapper = objectMapper;
    }

    public List<PlayerClueView> explore(String sessionId, String locationId, String objectHint) {
        if (locationId == null || locationId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<Clue> newlyUnlocked = clueUnlockService.exploreLocation(sessionId, locationId, objectHint);

        // exploreLocation()이 이미 EvidenceInventory를 저장한 뒤라, 문맥 필드 계산을 위해 최신 상태를 다시 읽는다.
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        EvidenceInventory inventory = evidenceInventoryRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        CaseBlueprint blueprint = objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);

        return newlyUnlocked.stream()
                .map(clue -> PlayerClueViewFactory.toView(clue, blueprint, inventory.getDiscoveredClueIds()))
                .toList();
    }
}
