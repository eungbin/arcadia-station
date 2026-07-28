package com.arcadia.station.service;

import com.arcadia.station.client.dto.GenerationResult;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;

/**
 * READY 응답을 GameSession에 반영하는 로직. 세션 생성 시 첫 폴링(GameSessionService)과
 * 그 이후 재폴링(CaseGenerationPollingService) 양쪽에서 공유한다.
 */
final class GenerationResultApplier {

    private GenerationResultApplier() {}

    static void apply(GameSession session, GenerationResult generation) {
        CaseBlueprint blueprint = generation.caseBlueprint();
        session.setBlueprintId(blueprint.blueprintId());
        session.setWorldTemplateId(blueprint.worldTemplate().id());
        session.setWorldTemplateVersion(blueprint.worldTemplate().version());
        session.setRuleTemplateId(blueprint.ruleTemplate().id());
        session.setRuleTemplateVersion(blueprint.ruleTemplate().version());
        session.setBlueprintSha256(generation.blueprintSha256());
        session.setGenerationSource(generation.generationSource());
        session.setGenerationAttemptCount(generation.generationAttemptCount());
        session.setModel(generation.model());
        session.setPromptVersion(generation.promptVersion());
        session.setCaseBlueprintJson(generation.rawCaseBlueprintJson());
        session.setFrozenAt(generation.frozenAt());
        // 7.1절: CaseBlueprint 저장(동결) 직후 BRIEFING으로 전환
        session.setState(SessionState.BRIEFING);
    }
}
