package com.arcadia.station.game.application;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.ArcadiaLocationRoster;
import com.arcadia.station.game.api.dto.PublicClueView;
import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.integration.FrontendIntegrationContract;
import com.arcadia.station.integration.FrontendIntegrationContractRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExplorationService {

    private final GameSessionService sessions;
    private final Map<String, FrontendIntegrationContract.InvestigationObjectRoute> objects;

    public ExplorationService(
            GameSessionService sessions,
            FrontendIntegrationContractRepository frontendContracts
    ) {
        this.sessions = sessions;
        this.objects = Map.copyOf(frontendContracts.contract().investigationObjects());
    }

    public ExplorationResult explore(
            String sessionId,
            String locationId,
            String objectHint
    ) {
        GameSession session = sessions.requireSession(sessionId);
        if (!ArcadiaLocationRoster.contains(locationId)) {
            throw new IllegalArgumentException(
                    "Unknown exploration locationId: " + locationId
            );
        }
        validateObjectHint(locationId, objectHint);
        CaseBlueprint blueprint = sessions.requireFrozenCase(sessionId).blueprint();
        session.startInvestigation();
        List<CaseBlueprint.Clue> found = blueprint.clues().stream()
                .filter(clue -> clue.acquisition().type() == CaseBlueprint.AcquisitionType.EXPLORE)
                .filter(clue -> locationId.equals(clue.acquisition().locationId()))
                .filter(clue -> objectHint == null
                        || objectHint.equals(clue.source().sourceId()))
                .filter(clue -> session.evidenceInventory()
                        .containsAll(clue.acquisition().requiredClueIds()))
                .filter(clue -> session.evidenceInventory().add(clue.clueId()))
                .toList();
        return new ExplorationResult(
                locationId,
                found.stream().map(PublicClueView::from).toList()
        );
    }

    private void validateObjectHint(String locationId, String objectHint) {
        if (objectHint == null) {
            return;
        }
        if (objectHint.isBlank()) {
            throw new IllegalArgumentException("objectHint cannot be blank");
        }
        FrontendIntegrationContract.InvestigationObjectRoute route = objects.get(objectHint);
        if (route == null
                || route.mode() != FrontendIntegrationContract.InspectionMode.EXPLORE) {
            throw new IllegalArgumentException(
                    "Unknown exploration objectHint: " + objectHint
            );
        }
        if (!locationId.equals(route.locationId())) {
            throw new IllegalArgumentException(
                    "objectHint does not belong to locationId: " + objectHint
            );
        }
    }

    public record ExplorationResult(String locationId, List<PublicClueView> newlyDiscoveredClues) {}
}
