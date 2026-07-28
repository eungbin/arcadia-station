package com.arcadia.station.service;

import com.arcadia.station.domain.caseblueprint.Clue;
import com.arcadia.station.dto.response.PlayerClueView;
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

    public List<PlayerClueView> explore(String sessionId, String locationId) {
        List<Clue> newlyUnlocked = clueUnlockService.exploreLocation(sessionId, locationId);
        return newlyUnlocked.stream()
                .map(clue -> new PlayerClueView(clue.clueId(), clue.title(), clue.clueType(), clue.playerText()))
                .toList();
    }
}
