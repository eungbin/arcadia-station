package com.arcadia.station.client;

import com.arcadia.station.client.dto.AssistantQueryResult;
import com.arcadia.station.client.dto.RagDiscoveredClueRef;
import com.arcadia.station.config.AiServerProperties;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 6장 계약(POST {AI_SERVER_BASE_URL}/api/v1/sessions/{aiCaseRequestId}/assistant/queries)의 실제 구현체.
 * AI 서버 회신(2026-07-29) 3.1절: 경로에는 aiCaseRequestId를 써야 한다(플레이어 sessionId 아님).
 */
@Component
@Profile("real-ai")
public class RealAssistantClient implements AssistantClient {

    private record QueryRequest(String question) {}

    private record NewlyDiscoveredClueWire(
        String clueId, String title, String clueType, List<String> solutionRoles, String playerText) {}

    private record QueryResponseWire(
        String answer,
        List<String> citedRecordIds,
        List<String> suggestedQueries,
        List<NewlyDiscoveredClueWire> newlyDiscoveredClues) {}

    private final RestClient restClient;
    private final String internalApiKey;

    public RealAssistantClient(AiServerProperties properties) {
        this.restClient = RestClient.create(properties.baseUrl());
        this.internalApiKey = properties.internalApiKey();
    }

    @Override
    public AssistantQueryResult query(String aiCaseRequestId, String question) {
        QueryResponseWire response;
        try {
            response = restClient.post()
                    .uri("/api/v1/sessions/{aiCaseRequestId}/assistant/queries", aiCaseRequestId)
                    .header("X-Internal-AI-Key", internalApiKey)
                    .body(new QueryRequest(question))
                    .retrieve()
                    .body(QueryResponseWire.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new AiSessionLostException("Assistant session not found: " + aiCaseRequestId);
        }

        // clueId 이외의 필드는 우리 쪽 CaseBlueprint를 신뢰 소스로 다시 조회하므로 여기서는 참조하지 않는다(6.2절 재검증).
        List<RagDiscoveredClueRef> newlyDiscoveredClues = response.newlyDiscoveredClues().stream()
                .map(wire -> new RagDiscoveredClueRef(wire.clueId()))
                .toList();
        return new AssistantQueryResult(
                response.answer(), response.citedRecordIds(), response.suggestedQueries(), newlyDiscoveredClues);
    }
}
