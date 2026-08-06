package com.arcadia.station.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.AssistantQueryRequest;
import com.arcadia.station.dto.request.DeductionRequest;
import com.arcadia.station.dto.request.ExploreRequest;
import com.arcadia.station.dto.request.SessionCreateRequest;
import com.arcadia.station.dto.response.AssistantQueryResponse;
import com.arcadia.station.dto.response.DeductionResult;
import com.arcadia.station.dto.response.FinalCaseReveal;
import com.arcadia.station.dto.response.SessionCreateResponse;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 탐사 + RAG로 네 역할 증거를 전부 모은 뒤 최종 추리를 제출해서 COMPLETED까지 가는 전체 플로우를 확인한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class DeductionControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 네_역할_증거를_모두_모으고_정답을_제출하면_COMPLETED_후_전체_진실이_공개된다() {
        String sessionId = createSession();

        explore(sessionId, "MEDICAL_BAY");
        explore(sessionId, "COMMON_AREA");
        assistantQuery(sessionId, "02:05 안전 진단 기록을 보여줘");
        explore(sessionId, "ENGINEERING_BAY");

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("SETUP", "CLUE-SETUP-LOG");
        evidence.put("TRIGGER", "CLUE-TRIGGER-LOG");
        evidence.put("OPPORTUNITY", "CLUE-ACCESS-HISTORY");
        evidence.put("MOTIVE", "CLUE-MOTIVE-MESSAGE");

        ResponseEntity<ApiResponse<DeductionResult>> deductionResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}/deductions",
                HttpMethod.POST,
                new HttpEntity<>(new DeductionRequest("SOPHIA", evidence, null)),
                new ParameterizedTypeReference<ApiResponse<DeductionResult>>() {},
                sessionId);
        assertThat(deductionResponse.getBody().data().verdict()).isEqualTo("CORRECT");

        ResponseEntity<ApiResponse<FinalCaseReveal>> resultResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}/result",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<FinalCaseReveal>>() {},
                sessionId);
        assertThat(resultResponse.getBody().data().culpritId()).isEqualTo("SOPHIA");
    }

    private void explore(String sessionId, String locationId) {
        restTemplate.exchange(
                "/api/v1/sessions/{id}/explore",
                HttpMethod.POST,
                new HttpEntity<>(new ExploreRequest(locationId, null)),
                Void.class,
                sessionId);
    }

    private void assistantQuery(String sessionId, String question) {
        restTemplate.exchange(
                "/api/v1/sessions/{id}/assistant/queries",
                HttpMethod.POST,
                new HttpEntity<>(new AssistantQueryRequest(question)),
                new ParameterizedTypeReference<ApiResponse<AssistantQueryResponse>>() {},
                sessionId);
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
