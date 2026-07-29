package com.arcadia.station.game.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.game.domain.SessionState;
import com.arcadia.station.infrastructure.persistence.InMemoryGameSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "arcadia.ai.offline-mode=true")
@AutoConfigureMockMvc
class GameFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryGameSessionRepository sessions;

    @Test
    void completesOfflineVerticalSliceWithoutLeakingSecrets() throws Exception {
        String createBody = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\":\"integration-seed\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CREATING"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String sessionId = objectMapper.readTree(createBody).path("sessionId").asText();
        awaitState(sessionId, SessionState.READY);

        String playerJson = mockMvc.perform(get("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.briefing").isString())
                .andExpect(jsonPath("$.discoveredClues").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(playerJson)
                .doesNotContain("\"culpritId\"")
                .doesNotContain("\"truthSummary\"")
                .doesNotContain("\"actualWhereabouts\"")
                .doesNotContain("\"solution\"");

        mockMvc.perform(post("/api/v1/sessions/{id}/explore", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"LIFE_SUPPORT_CONTROL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyDiscoveredClues[0].clueId")
                        .value("CLUE-SETUP-PANEL"));

        String ragJson = mockMvc.perform(post(
                                "/api/v1/sessions/{id}/assistant/queries",
                                sessionId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "소피아 제어실 권한 안전 진단 의료 연구 감사 면담 마야 심야"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citedRecordIds").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ragJson)
                .contains("CLUE-TRIGGER-LOG")
                .contains("CLUE-ACCESS-HISTORY")
                .contains("CLUE-MOTIVE-AUDIT");

        mockMvc.perform(post(
                                "/api/v1/sessions/{id}/interrogations/SOPHIA/turns",
                                sessionId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "안전 점검 기록을 설명해 주세요.",
                                  "presentedClueIds": ["CLUE-SETUP-PANEL", "CLUE-TRIGGER-LOG"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedQuestions.length()").value(2))
                .andExpect(jsonPath("$.revealedFactIds[0]").value("FACT-SETUP"));

        mockMvc.perform(post("/api/v1/sessions/{id}/deductions", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "culpritId": "SOPHIA",
                                  "evidenceByRole": {
                                    "SETUP": "CLUE-SETUP-PANEL",
                                    "TRIGGER": "CLUE-TRIGGER-LOG",
                                    "OPPORTUNITY": "CLUE-ACCESS-HISTORY",
                                    "MOTIVE": "CLUE-MOTIVE-AUDIT"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("CORRECT"))
                .andExpect(jsonPath("$.culpritCorrect").value(true));

        mockMvc.perform(get("/api/v1/sessions/{id}/result", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.culpritId").value("SOPHIA"))
                .andExpect(jsonPath("$.truthSummary").isString());
    }

    @Test
    void internalBackendContractReturnsFrozenPrivatePayloadOnlyWhenReady() throws Exception {
        String accepted = mockMvc.perform(post("/internal/v1/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "backend_contract_001",
                                  "seed": "backend-seed"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CREATING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sessionId = objectMapper.readTree(accepted).path("sessionId").asText();
        awaitState(sessionId, SessionState.READY);

        String readyResponse = mockMvc.perform(get("/internal/v1/cases/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.generation.blueprintSha256").isString())
                .andExpect(jsonPath("$.generation.generationSource").value("FALLBACK"))
                .andExpect(jsonPath("$.generation.caseBlueprint.culpritId").value("SOPHIA"))
                .andExpect(jsonPath("$.generation.caseBlueprint.solution").isMap())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode actualResponse = objectMapper.readTree(readyResponse);
        JsonNode documentedResponse = objectMapper.readTree(
                Files.readString(
                        Path.of(
                                "docs",
                                "examples",
                                "internal-case-ready.response.json"
                        ),
                        StandardCharsets.UTF_8
                )
        );
        removeDynamicTimestamps(actualResponse);
        removeDynamicTimestamps(documentedResponse);
        assertThat(actualResponse).isEqualTo(documentedResponse);
    }

    private void removeDynamicTimestamps(JsonNode response) {
        ObjectNode generation = (ObjectNode) response.path("generation");
        generation.remove("createdAt");
        generation.remove("frozenAt");
    }

    private void awaitState(String sessionId, SessionState expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            GameSession session = sessions.findById(sessionId).orElseThrow();
            if (session.state() == expected) {
                return;
            }
            if (session.state() == SessionState.FAILED) {
                throw new AssertionError("Session generation failed: " + session.failureCode());
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for state " + expected);
    }
}
