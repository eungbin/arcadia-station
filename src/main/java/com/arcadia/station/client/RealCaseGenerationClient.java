package com.arcadia.station.client;

import com.arcadia.station.client.dto.CaseGenerationAck;
import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.client.dto.GenerationResult;
import com.arcadia.station.config.AiServerProperties;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 4장 계약(POST/GET /internal/v1/cases)의 실제 구현체. AI 서버 base URL이 아직 확정되지
 * 않았고(16.2절) 실제 서버 검증이 불가능하므로, "real-ai" 프로파일을 켰을 때만 활성화된다.
 * ponytail: connect/per-attempt timeout(12장 설정값)을 RestClient의 요청 팩토리에 아직
 * 연결하지 않음 — 실제 AI 서버가 배포되어 검증 가능해지면 ClientHttpRequestFactory에 반영할 것.
 */
@Component
@Profile("real-ai")
public class RealCaseGenerationClient implements CaseGenerationClient {

    private record CaseCreateRequest(String sessionId, String seed) {}

    private record CaseCreateResponse(String sessionId, String status, String statusUrl) {}

    private record GenerationWire(
        CaseBlueprint caseBlueprint,
        String blueprintSha256,
        Integer generationAttemptCount,
        String generationSource,
        String model,
        String promptVersion,
        Instant createdAt,
        Instant frozenAt) {}

    private record CaseStatusResponse(String sessionId, String status, GenerationWire generation, String errorCode) {}

    private final RestClient restClient;
    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    public RealCaseGenerationClient(AiServerProperties properties, ObjectMapper objectMapper) {
        this.restClient = RestClient.create(properties.baseUrl());
        this.internalApiKey = properties.internalApiKey();
        this.objectMapper = objectMapper;
    }

    @Override
    public CaseGenerationAck requestCase(String aiCaseRequestId, String seed) {
        CaseCreateResponse response = restClient.post()
                .uri("/internal/v1/cases")
                .header("X-Internal-AI-Key", internalApiKey)
                .body(new CaseCreateRequest(aiCaseRequestId, seed))
                .retrieve()
                .body(CaseCreateResponse.class);
        return new CaseGenerationAck(response.sessionId(), response.status(), response.statusUrl());
    }

    @Override
    public CaseGenerationStatus pollStatus(String aiCaseRequestId) {
        String rawBody;
        try {
            rawBody = restClient.get()
                    .uri("/internal/v1/cases/{id}", aiCaseRequestId)
                    .header("X-Internal-AI-Key", internalApiKey)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AiSessionLostException("Case generation session not found: " + aiCaseRequestId);
        }

        JsonNode tree = objectMapper.readTree(rawBody);
        CaseStatusResponse response = objectMapper.treeToValue(tree, CaseStatusResponse.class);

        GenerationResult generation = null;
        if (response.generation() != null) {
            String rawCaseBlueprintJson = tree.path("generation").path("caseBlueprint").toString();
            GenerationWire wire = response.generation();
            generation = new GenerationResult(
                    wire.caseBlueprint(),
                    rawCaseBlueprintJson,
                    wire.blueprintSha256(),
                    wire.generationAttemptCount(),
                    wire.generationSource(),
                    wire.model(),
                    wire.promptVersion(),
                    wire.createdAt(),
                    wire.frozenAt());
        }
        return new CaseGenerationStatus(response.sessionId(), response.status(), generation, response.errorCode());
    }
}
