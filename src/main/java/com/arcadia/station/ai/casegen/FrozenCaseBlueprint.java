package com.arcadia.station.ai.casegen;

import java.time.Instant;

public record FrozenCaseBlueprint(
        String sessionId,
        String blueprintId,
        String worldTemplateId,
        String worldTemplateVersion,
        String ruleTemplateId,
        String ruleTemplateVersion,
        String seed,
        CaseBlueprint blueprint,
        String blueprintJson,
        String blueprintSha256,
        int generationAttemptCount,
        GenerationSource generationSource,
        String model,
        String promptVersion,
        Instant createdAt,
        Instant frozenAt
) {}
