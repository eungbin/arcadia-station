package com.arcadia.station.dto.response;

import com.arcadia.station.domain.caseblueprint.ClueType;
import java.util.List;

public record PlayerClueView(
        String clueId,
        String title,
        ClueType clueType,
        String playerText,
        boolean isCore,
        List<RevealedFactView> revealedFacts,
        List<String> linkedClueIds,
        List<SuspectEffectView> suspectEffects,
        boolean hasPendingConnection) {}
