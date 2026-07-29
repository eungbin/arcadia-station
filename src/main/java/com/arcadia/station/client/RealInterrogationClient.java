package com.arcadia.station.client;

import com.arcadia.station.client.dto.NpcTurnResult;
import com.arcadia.station.client.dto.RecommendedQuestion;
import com.arcadia.station.config.AiServerProperties;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 5장 계약(POST {AI_SERVER_BASE_URL}/api/v1/sessions/{aiCaseRequestId}/interrogations/{characterId}/turns)의 실제 구현체.
 * AI 서버 회신(2026-07-29) 3.1절: 경로에는 aiCaseRequestId를 써야 한다(플레이어 sessionId 아님).
 */
@Component
@Profile("real-ai")
public class RealInterrogationClient implements InterrogationClient {

    private record TurnRequest(String question, List<String> presentedClueIds) {}

    private final RestClient restClient;
    private final String internalApiKey;

    public RealInterrogationClient(AiServerProperties properties) {
        this.restClient = RestClient.create(properties.baseUrl());
        this.internalApiKey = properties.internalApiKey();
    }

    @Override
    public NpcTurnResult ask(String aiCaseRequestId, String characterId, String question, List<String> presentedClueIds) {
        try {
            return restClient.post()
                    .uri("/api/v1/sessions/{aiCaseRequestId}/interrogations/{characterId}/turns", aiCaseRequestId, characterId)
                    .header("X-Internal-AI-Key", internalApiKey)
                    .body(new TurnRequest(question, presentedClueIds))
                    .retrieve()
                    .body(NpcTurnResultWire.class)
                    .toResult();
        } catch (HttpClientErrorException.NotFound e) {
            throw new AiSessionLostException("Interrogation session not found: " + aiCaseRequestId);
        }
    }

    private record NpcTurnResultWire(
        String dialogue, String emotion, List<String> revealedFactIds, List<RecommendedQuestion> recommendedQuestions) {
        NpcTurnResult toResult() {
            return new NpcTurnResult(dialogue, emotion, revealedFactIds, recommendedQuestions);
        }
    }
}
