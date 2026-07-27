package com.arcadia.station.game.api.dto;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import java.util.List;

public record PublicClueView(
        String clueId,
        String title,
        CaseBlueprint.ClueType clueType,
        List<CaseBlueprint.EvidenceRole> solutionRoles,
        String playerText
) {
    public static PublicClueView from(CaseBlueprint.Clue clue) {
        return new PublicClueView(
                clue.clueId(),
                clue.title(),
                clue.clueType(),
                clue.solutionRoles(),
                clue.playerText()
        );
    }
}
