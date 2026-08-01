package com.arcadia.station.game.api.dto;

import com.arcadia.station.game.domain.SessionState;
import java.util.List;

public record PlayerCaseView(
        String sessionId,
        SessionState state,
        String title,
        String briefing,
        WorldView world,
        List<SuspectView> suspects,
        List<LocationView> availableLocations,
        List<PublicClueView> discoveredClues,
        int remainingDeductionAttempts
) {
    public record WorldView(String name, String summary, List<String> publicFacts) {}

    public record SuspectView(
            String id,
            String displayName,
            String occupation,
            String publicProfile,
            List<String> personalityTraits
    ) {}

    public record LocationView(String id, String displayName, String publicDescription) {}
}
