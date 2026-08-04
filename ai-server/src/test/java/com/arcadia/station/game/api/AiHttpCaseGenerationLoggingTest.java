package com.arcadia.station.game.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.CaseBlueprintGenerator;
import com.arcadia.station.ai.casegen.CaseGenerationRequest;
import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
                "arcadia.ai.offline-mode=false",
                "arcadia.ai.provider=openai",
                "arcadia.ai.openai.api-key=HTTP_AI_API_KEY_DO_NOT_LOG",
                "arcadia.ai.case-generation.max-retries=0",
                "arcadia.internal-api-key=HTTP_AI_INTERNAL_KEY_DO_NOT_LOG"
        }
)
@ExtendWith(OutputCaptureExtension.class)
class AiHttpCaseGenerationLoggingTest {

    private static final String SESSION_ID = "http-ai-log-test-01";
    private static final String SEED = "HTTP_AI_SEED_DO_NOT_LOG";
    private static final String INTERNAL_KEY = "HTTP_AI_INTERNAL_KEY_DO_NOT_LOG";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FallbackCaseProvider fallback;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CaseBlueprintGenerator generator;

    @BeforeEach
    void returnAValidGeneratedBlueprint() {
        when(generator.generate(any(CaseGenerationRequest.class)))
                .thenAnswer(invocation -> {
                    CaseGenerationRequest request = invocation.getArgument(0);
                    CaseBlueprint generated = fallback.forSession(
                            request.sessionId(),
                            request.seed()
                    );
                    ObjectNode generatedNode = objectMapper.valueToTree(generated);
                    ObjectNode firstClue = (ObjectNode) generatedNode
                            .withArray("clues")
                            .get(0);
                    firstClue.put(
                            "title",
                            "seed=" + request.seed()
                                    + " \" sourceId=FORGED\u001b[31m\nFORGED_LINE"
                    );
                    return objectMapper.treeToValue(
                            generatedNode,
                            CaseBlueprint.class
                    );
                });
    }

    @Test
    void realHttpSessionStartLogsApiModeAndEveryGeneratedClue(
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
        JsonNode ready = awaitReady(headers);
        awaitLog(output, "[GAME-SESSION][READY] event=session_case_ready sessionId="
                + SESSION_ID);

        assertThat(ready.at("/generation/generationSource").asText()).isEqualTo("AI");
        List<String> responseClueIds = new ArrayList<>();
        ready.at("/generation/caseBlueprint/clues")
                .forEach(clue -> responseClueIds.add(clue.path("clueId").asText()));
        int responseClueCount = responseClueIds.size();
        assertThat(responseClueCount).isEqualTo(14);
        verify(generator, times(1)).generate(any(CaseGenerationRequest.class));

        String logs = auditLogs(output);
        assertThat(logs)
                .contains("[AI-MODE] event=ai_runtime_configured configuredMode=API "
                        + "selectedGateway=OPENAI externalAiEnabled=true "
                        + "fallbackReason=NONE")
                .contains("[AI-CASE][START] event=ai_case_start sessionId="
                        + SESSION_ID + " configuredMode=API fallbackReason=NONE")
                .contains("[AI-CASE][RESULT] event=ai_case_result sessionId="
                        + SESSION_ID + " mode=API generationSource=AI "
                        + "fallbackReason=NONE aiPathAttempted=true")
                .contains("[AI-CASE][END] event=ai_case_clue_list_complete sessionId="
                        + SESSION_ID + " mode=API loggedClueCount=14 "
                        + "totalClueCount=14 clueManifestTruncated=false")
                .contains("[GAME-SESSION][READY] event=session_case_ready sessionId="
                        + SESSION_ID + " mode=API generationSource=AI")
                .contains("[REDACTED_SEED]")
                .contains("title=\"seed=[REDACTED_SEED] \\\" "
                        + "sourceId=FORGED_[31m_FORGED_LINE\" clueType=")
                .doesNotContain(SEED)
                .doesNotContain("\u001b")
                .doesNotContain("\nFORGED_LINE")
                .doesNotContain("HTTP_AI_API_KEY_DO_NOT_LOG")
                .doesNotContain(INTERNAL_KEY);
        List<String> clueLogLines = logs.lines()
                .filter(line -> line.contains("[AI-CASE][CLUE]"))
                .filter(line -> line.contains("sessionId=" + SESSION_ID))
                .toList();
        assertThat(clueLogLines).hasSize(responseClueCount);
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
}
