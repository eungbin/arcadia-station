package com.arcadia.station.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.ExploreRequest;
import com.arcadia.station.dto.request.SessionCreateRequest;
import com.arcadia.station.dto.response.PlayerCaseView;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.dto.response.SessionCreateResponse;
import java.util.List;
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ExplorationControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 장소를_탐사하면_단서가_해금되고_PlayerCaseView에_반영된다() {
        String sessionId = createSession();

        ResponseEntity<ApiResponse<List<PlayerClueView>>> exploreResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}/explore",
                HttpMethod.POST,
                new HttpEntity<>(new ExploreRequest("PERSONAL_QUARTERS", null)),
                new ParameterizedTypeReference<ApiResponse<List<PlayerClueView>>>() {},
                sessionId);

        List<PlayerClueView> unlocked = exploreResponse.getBody().data();
        assertThat(unlocked).extracting(PlayerClueView::clueId).containsExactly("CLUE-MOTIVE-MESSAGE");

        ResponseEntity<ApiResponse<PlayerCaseView>> viewResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<PlayerCaseView>>() {},
                sessionId);
        assertThat(viewResponse.getBody().data().discoveredClues())
                .extracting(PlayerClueView::clueId)
                .containsExactly("CLUE-MOTIVE-MESSAGE");
    }

    @Test
    void 해금_조건에_맞지_않는_장소를_탐사하면_빈_목록을_반환한다() {
        String sessionId = createSession();

        ResponseEntity<ApiResponse<List<PlayerClueView>>> exploreResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}/explore",
                HttpMethod.POST,
                new HttpEntity<>(new ExploreRequest("UNKNOWN_LOCATION", null)),
                new ParameterizedTypeReference<ApiResponse<List<PlayerClueView>>>() {},
                sessionId);

        assertThat(exploreResponse.getBody().data()).isEmpty();
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
