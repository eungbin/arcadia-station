package com.arcadia.station.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "debug=false",
                "logging.level.root=WARN",
                "arcadia.ai.enabled=true",
                "arcadia.ai.offline-mode=true",
                "arcadia.internal-api-key=HTTP_FALLBACK_KEY_DO_NOT_LOG"
        }
)
@ExtendWith(OutputCaptureExtension.class)
class FallbackHttpCaseGenerationLoggingTest {

    private static final String SESSION_ID = "http-fallback-log-01";
    private static final String SEED = "HTTP_FALLBACK_SEED_DO_NOT_LOG";
    private static final String INTERNAL_KEY = "HTTP_FALLBACK_KEY_DO_NOT_LOG";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void realHttpSessionStartLogsFallbackModeAndEveryGeneratedClue(
            CapturedOutput output
    ) throws Exception {
        HttpHeaders headers = internalHeaders();
        ResponseEntity<JsonNode> accepted = restTemplate.exchange(
                caseCollectionUrl(),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "sessionId", SESSION_ID,
                        "seed", SEED
                ), headers),
                JsonNode.class
        );

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(accepted.getBody()).isNotNull();
        assertThat(accepted.getBody().path("sessionId").asText()).isEqualTo(SESSION_ID);

        JsonNode ready = awaitReady(headers);
        awaitLog(output, "[GAME-SESSION][READY] event=session_case_ready sessionId="
                + SESSION_ID);

        assertThat(ready.at("/generation/generationSource").asText())
                .isEqualTo("FALLBACK");
        List<String> responseClueIds = new ArrayList<>();
        ready.at("/generation/caseBlueprint/clues")
                .forEach(clue -> responseClueIds.add(clue.path("clueId").asText()));
        assertThat(responseClueIds).hasSize(14);

        String logs = auditLogs(output);
        assertThat(logs)
                .contains("[AI-MODE] event=ai_runtime_configured "
                        + "configuredMode=FALLBACK selectedGateway=FALLBACK "
                        + "externalAiEnabled=false fallbackReason=OFFLINE_MODE")
                .contains("[GAME-SESSION][START] event=session_case_accepted sessionId="
                        + SESSION_ID)
                .contains("[AI-CASE][START] event=ai_case_start sessionId="
                        + SESSION_ID + " configuredMode=FALLBACK "
                        + "fallbackReason=OFFLINE_MODE")
                .contains("[AI-CASE][RESULT] event=ai_case_result sessionId="
                        + SESSION_ID + " mode=FALLBACK generationSource=FALLBACK "
                        + "fallbackReason=OFFLINE_MODE aiPathAttempted=false")
                .contains("[AI-CASE][END] event=ai_case_clue_list_complete sessionId="
                        + SESSION_ID + " mode=FALLBACK loggedClueCount=14 "
                        + "totalClueCount=14 clueManifestTruncated=false")
                .contains("[GAME-SESSION][READY] event=session_case_ready sessionId="
                        + SESSION_ID + " mode=FALLBACK generationSource=FALLBACK")
                .doesNotContain(SEED)
                .doesNotContain(INTERNAL_KEY);

        List<String> clueLogLines = logs.lines()
                .filter(line -> line.contains("[AI-CASE][CLUE]"))
                .filter(line -> line.contains("sessionId=" + SESSION_ID))
                .toList();
        assertThat(clueLogLines).hasSize(responseClueIds.size());
        assertThat(responseClueIds).doesNotHaveDuplicates();
        assertThat(clueLogLines).allSatisfy(line -> assertThat(line)
                .contains(
                        "number=",
                        "clueId=",
                        "title=\"",
                        "clueType=",
                        "core=",
                        "acquisitionType=",
                        "locationId=",
                        "sourceType=",
                        "sourceId="
                ));
        responseClueIds.forEach(clueId -> assertThat(clueLogLines.stream()
                .filter(line -> line.contains("clueId=" + clueId + " "))
                .count()).isEqualTo(1));

        assertInOrder(
                logs,
                "[GAME-SESSION][START] event=session_case_accepted sessionId=" + SESSION_ID,
                "[AI-CASE][START] event=ai_case_start sessionId=" + SESSION_ID,
                "[AI-CASE][RESULT] event=ai_case_result sessionId=" + SESSION_ID,
                "[AI-CASE][END] event=ai_case_clue_list_complete sessionId=" + SESSION_ID,
                "[GAME-SESSION][READY] event=session_case_ready sessionId=" + SESSION_ID
        );
    }

    private JsonNode awaitReady(HttpHeaders headers) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    caseCollectionUrl() + "/" + SESSION_ID,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = response.getBody();
            assertThat(body).isNotNull();
            String status = body.path("status").asText();
            if ("READY".equals(status)) {
                return body;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("HTTP case generation failed: "
                        + body.path("errorCode").asText());
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for HTTP case generation");
    }

    private void awaitLog(CapturedOutput output, String marker) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (output.getOut().contains(marker)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for log marker: " + marker);
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-AI-Key", INTERNAL_KEY);
        return headers;
    }

    private String auditLogs(CapturedOutput output) {
        return String.join("\n", output.getOut().lines()
                .filter(line -> line.contains("ARC_AI_CASE_AUDIT"))
                .toList());
    }

    private String caseCollectionUrl() {
        return "http://127.0.0.1:" + port + "/internal/v1/cases";
    }

    private void assertInOrder(String logs, String... markers) {
        int previousIndex = -1;
        for (String marker : markers) {
            int currentIndex = logs.indexOf(marker);
            assertThat(currentIndex)
                    .as("log marker order for %s", marker)
                    .isGreaterThan(previousIndex);
            previousIndex = currentIndex;
        }
    }
}
