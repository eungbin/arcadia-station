package com.arcadia.station.service;

import com.arcadia.station.domain.caseblueprint.AcquisitionType;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.Clue;
import com.arcadia.station.domain.caseblueprint.Fact;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.dto.response.RevealedFactView;
import com.arcadia.station.dto.response.SuspectEffectView;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * 요청 A(FRONTEND_BACKEND_CLUE_CONTEXT_REQUEST_2026-08-06.md): PlayerClueView에 단서 문맥 필드를 채운다.
 * revealedFacts는 truthValue를 담지 않고, linkedClueIds는 발견한 단서로만 제한하며, hasPendingConnection은
 * 대상 clueId 없이 boolean만 노출한다 — 문서 3.2절의 스포일러 경계를 그대로 지킨다.
 */
final class PlayerClueViewFactory {

    private PlayerClueViewFactory() {}

    static PlayerClueView toView(Clue clue, CaseBlueprint blueprint, Set<String> discoveredClueIds) {
        var revealedFacts = clue.revealsFactIds().stream()
                .map(factId -> findFact(blueprint, factId))
                .filter(Objects::nonNull)
                .map(fact -> new RevealedFactView(fact.factId(), fact.statement()))
                .toList();

        var linkedClueIds = blueprint.clues().stream()
                .filter(other -> !other.clueId().equals(clue.clueId()))
                .filter(other -> discoveredClueIds.contains(other.clueId()))
                .filter(other -> !Collections.disjoint(other.revealsFactIds(), clue.revealsFactIds()))
                .map(Clue::clueId)
                .toList();

        var suspectEffects = clue.suspectEffects().stream()
                .map(effect -> new SuspectEffectView(effect.characterId(), effect.effect()))
                .toList();

        boolean hasPendingConnection = blueprint.clues().stream()
                .anyMatch(other -> other.acquisition().type() == AcquisitionType.CONNECT
                        && !discoveredClueIds.contains(other.clueId())
                        && other.acquisition().requiredClueIds().contains(clue.clueId()));

        return new PlayerClueView(
                clue.clueId(),
                clue.title(),
                clue.clueType(),
                clue.playerText(),
                clue.isCore(),
                revealedFacts,
                linkedClueIds,
                suspectEffects,
                hasPendingConnection);
    }

    private static Fact findFact(CaseBlueprint blueprint, String factId) {
        return blueprint.facts().stream()
                .filter(fact -> fact.factId().equals(factId))
                .findFirst()
                .orElse(null);
    }
}
