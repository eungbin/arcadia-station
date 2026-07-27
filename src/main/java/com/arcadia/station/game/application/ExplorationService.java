package com.arcadia.station.game.application;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.game.api.dto.PublicClueView;
import com.arcadia.station.game.domain.GameSession;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExplorationService {

    private final GameSessionService sessions;

    public ExplorationService(GameSessionService sessions) {
        this.sessions = sessions;
    }

    public ExplorationResult explore(String sessionId, String locationId) {
        GameSession session = sessions.requireSession(sessionId);
        CaseBlueprint blueprint = sessions.requireFrozenCase(sessionId).blueprint();
        session.startInvestigation();
        List<CaseBlueprint.Clue> found = blueprint.clues().stream()
                .filter(clue -> clue.acquisition().type() == CaseBlueprint.AcquisitionType.EXPLORE)
                .filter(clue -> locationId.equals(clue.acquisition().locationId()))
                .filter(clue -> session.evidenceInventory()
                        .containsAll(clue.acquisition().requiredClueIds()))
                .filter(clue -> session.evidenceInventory().add(clue.clueId()))
                .toList();
        return new ExplorationResult(
                locationId,
                found.stream().map(PublicClueView::from).toList()
        );
    }

    public record ExplorationResult(String locationId, List<PublicClueView> newlyDiscoveredClues) {}
}
