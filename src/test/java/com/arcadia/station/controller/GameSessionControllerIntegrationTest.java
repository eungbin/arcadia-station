package com.arcadia.station.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.common.ApiResponse;
import com.arcadia.station.dto.request.SessionCreateRequest;
import com.arcadia.station.dto.response.PlayerCaseView;
import com.arcadia.station.dto.response.SessionCreateResponse;
import com.arcadia.station.dto.response.SessionStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GameSessionControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 세션_생성부터_PlayerCaseView_조회까지_Fake_클라이언트로_전체_플로우가_통과한다() {
        ResponseEntity<ApiResponse<SessionCreateResponse>> createResponse = restTemplate.exchange(
                "/api/v1/sessions",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new SessionCreateRequest(null)),
                new ParameterizedTypeReference<ApiResponse<SessionCreateResponse>>() {});

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String sessionId = createResponse.getBody().data().sessionId();
        // Fake 클라이언트는 즉시 READY를 반환하므로 첫 응답에서 이미 BRIEFING까지 진행된다.
        assertThat(createResponse.getBody().data().status()).isEqualTo("BRIEFING");

        ResponseEntity<ApiResponse<SessionStatusResponse>> statusResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}/status",
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<SessionStatusResponse>>() {},
                sessionId);
        assertThat(statusResponse.getBody().data().status()).isEqualTo("BRIEFING");

        ResponseEntity<ApiResponse<PlayerCaseView>> viewResponse = restTemplate.exchange(
                "/api/v1/sessions/{id}",
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<PlayerCaseView>>() {},
                sessionId);
        PlayerCaseView view = viewResponse.getBody().data();
        assertThat(view.title()).isEqualTo("아르카디아 스테이션 사건");
        assertThat(view.briefing()).isNotBlank();
        // 아직 탐사 전이므로 발견한 단서는 없어야 한다 (10장 보안 경계)
        assertThat(view.discoveredClues()).isEmpty();
        // 심문 UI용 용의자 목록은 탐사 여부와 무관하게 처음부터 전부 노출된다
        assertThat(view.suspectCharacterIds()).containsExactly("SOPHIA");
        // 탐사 UI용 장소 목록도 마찬가지로 처음부터 전부 노출된다
        assertThat(view.exploreLocationIds())
                .containsExactlyInAnyOrder("MEDICAL_BAY", "LIFE_SUPPORT_CORRIDOR", "PERSONAL_QUARTERS");
    }

    @Test
    void 존재하지_않는_세션을_조회하면_404를_반환한다() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/v1/sessions/{id}/status",
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {},
                "game_not_exists");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().success()).isFalse();
    }
}
