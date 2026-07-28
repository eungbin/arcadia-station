package com.arcadia.station.client.dto;

import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import java.time.Instant;

/**
 * 4.2절: READY 응답과 함께 오는 메타데이터. GameSession에 그대로 저장한다.
 */
public record GenerationResult(
    CaseBlueprint caseBlueprint,
    String rawCaseBlueprintJson,
    String blueprintSha256,
    Integer generationAttemptCount,
    String generationSource,
    String model,
    String promptVersion,
    Instant createdAt,
    Instant frozenAt
) {}
