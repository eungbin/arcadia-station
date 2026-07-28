package com.arcadia.station.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.AssistantQueryRequest;
import com.arcadia.station.dto.request.InterrogationTurnRequest;
import com.arcadia.station.dto.request.SessionCreateRequest;
import com.arcadia.station.dto.response.AssistantQueryResponse;
import com.arcadia.station.dto.response.NpcTurnResponse;
import com.arcadia.station.dto.response.SessionCreateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Fake AssistantClient/InterrogationClient를 통해 RAG로 단서를 해금하고,
 * 그 단서를 심문에서 제시했을 때 실제로 사실이 공개되는지 전체 플로우로 확인한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InterrogationAssistantIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void RAG로_해금한_단서를_심문에서_제시하면_사실이_공개된다() {
        String sessionId = createSession();

        ResponseEntity<ApiResponse<AssistantQueryResponse>> ragResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}/assistant/queries",
                HttpMethod.POST,
                new HttpEntity<>(new AssistantQueryRequest("02:05 안전 진단 기록을 보여줘")),
                new ParameterizedTypeReference<ApiResponse<AssistantQueryResponse>>() {},
                sessionId);
        assertThat(ragResponse.getBody().data().newlyDiscoveredClues())
                .extracting(c -> c.clueId())
                .contains("CLUE-TRIGGER-LOG");

        ResponseEntity<ApiResponse<NpcTurnResponse>> turnResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}/interrogations/{characterId}/turns",
                HttpMethod.POST,
                new HttpEntity<>(new InterrogationTurnRequest("그 기록을 설명해 주세요.", java.util.List.of("CLUE-TRIGGER-LOG"))),
                new ParameterizedTypeReference<ApiResponse<NpcTurnResponse>>() {},
                sessionId,
                "SOPHIA");

        assertThat(turnResponse.getBody().data().revealedFactIds()).contains("FACT-TRIGGER");
    }

    private String createSession() {
        ResponseEntity<ApiResponse<SessionCreateResponse>> createResponse = restTemplate.exchange(
                "/api/v1/sessions",
                HttpMethod.POST,
                new HttpEntity<>(new SessionCreateRequest(null)),
                new ParameterizedTypeReference<ApiResponse<SessionCreateResponse>>() {});
        return createResponse.getBody().data().sessionId();
    }
}
