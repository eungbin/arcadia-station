package com.arcadia.station.service;

import com.arcadia.station.domain.caseblueprint.Clue;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExplorationService {

    private final ClueUnlockService clueUnlockService;

    public ExplorationService(ClueUnlockService clueUnlockService) {
        this.clueUnlockService = clueUnlockService;
    }

    public List<PlayerClueView> explore(String sessionId, String locationId, String objectHint) {
        if (locationId == null || locationId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<Clue> newlyUnlocked = clueUnlockService.exploreLocation(sessionId, locationId, objectHint);
        return newlyUnlocked.stream()
                .map(clue -> new PlayerClueView(clue.clueId(), clue.title(), clue.clueType(), clue.playerText()))
                .toList();
    }
}
