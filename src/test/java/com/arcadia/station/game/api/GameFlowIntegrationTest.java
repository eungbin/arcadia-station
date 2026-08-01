package com.arcadia.station.game.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.game.domain.SessionState;
import com.arcadia.station.ai.template.ArcadiaLocationRoster;
import com.arcadia.station.infrastructure.persistence.InMemoryGameSessionRepository;
import com.arcadia.station.integration.FrontendIntegrationContract;
import com.arcadia.station.integration.FrontendIntegrationContractRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "arcadia.ai.offline-mode=true",
        "arcadia.internal-api-key=test-internal-key"
})
@AutoConfigureMockMvc
class GameFlowIntegrationTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryGameSessionRepository sessions;

    @Autowired
    private FrontendIntegrationContractRepository frontendContracts;

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
                        .content("{\"locationId\":\"CENTRAL_HUB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyDiscoveredClues[0].clueId")
                        .value("CLUE-SETUP-PANEL"));

        String ragJson = mockMvc.perform(post(
                                "/api/v1/sessions/{id}/assistant/queries",
                                sessionId
                        )
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
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
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
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
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
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

        String readyResponse = mockMvc.perform(get("/internal/v1/cases/{id}", sessionId)
                        .header("X-Internal-AI-Key", INTERNAL_KEY))
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

    @Test
    void acceptsBackendDiscoveredExploreClueWithoutAiExploreCall() throws Exception {
        String sessionId = "backend_explore_boundary_001";
        mockMvc.perform(post("/internal/v1/cases")
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "backend_explore_boundary_001",
                                  "seed": "backend-explore-boundary"
                                }
                                """))
                .andExpect(status().isAccepted());
        awaitState(sessionId, SessionState.READY);

        mockMvc.perform(post(
                                "/api/v1/sessions/{id}/assistant/queries",
                                sessionId
                        )
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "02:05"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyDiscoveredClues[0].clueId")
                        .value("CLUE-TRIGGER-LOG"));

        mockMvc.perform(post(
                                "/api/v1/sessions/{id}/interrogations/SOPHIA/turns",
                                sessionId
                        )
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "제시한 두 기록을 설명해 주세요.",
                                  "presentedClueIds": [
                                    "CLUE-TRIGGER-LOG",
                                    "CLUE-SETUP-PANEL"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealedFactIds[0]").value("FACT-SETUP"));

        mockMvc.perform(post(
                                "/api/v1/sessions/{id}/interrogations/SOPHIA/turns",
                                sessionId
                        )
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "존재하지 않는 단서를 확인해 주세요.",
                                  "presentedClueIds": ["CLUE-NOT-IN-FROZEN-CASE"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void interrogatesEveryCharacterExposedByAlibis() throws Exception {
        String sessionId = "backend_all_suspects_001";
        mockMvc.perform(post("/internal/v1/cases")
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "backend_all_suspects_001",
                                  "seed": "backend-all-suspects"
                                }
                                """))
                .andExpect(status().isAccepted());
        awaitState(sessionId, SessionState.READY);

        String readyResponse = mockMvc.perform(get("/internal/v1/cases/{id}", sessionId)
                        .header("X-Internal-AI-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> alibiCharacterIds = new ArrayList<>();
        objectMapper.readTree(readyResponse)
                .path("generation")
                .path("caseBlueprint")
                .path("alibis")
                .forEach(alibi -> alibiCharacterIds.add(alibi.path("characterId").asText()));
        assertThat(alibiCharacterIds)
                .containsExactlyInAnyOrder("MAYA", "JUNHO", "SOPHIA", "KASIM", "YUNA");

        for (String characterId : alibiCharacterIds) {
            mockMvc.perform(post(
                                    "/api/v1/sessions/{id}/interrogations/{characterId}/turns",
                                    sessionId,
                                    characterId
                            )
                            .header("X-Internal-AI-Key", INTERNAL_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "question": "사건 당시 어디에 있었는지 설명해 주세요.",
                                      "presentedClueIds": []
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recommendedQuestions.length()").value(2));
        }
    }

    @Test
    void exploresEveryRosterLocationAndRejectsUnknownLocation() throws Exception {
        String sessionId = "backend_all_locations_001";
        mockMvc.perform(post("/internal/v1/cases")
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "backend_all_locations_001",
                                  "seed": "backend-all-locations"
                                }
                                """))
                .andExpect(status().isAccepted());
        awaitState(sessionId, SessionState.READY);

        for (String locationId : ArcadiaLocationRoster.IDS) {
            mockMvc.perform(post("/api/v1/sessions/{id}/explore", sessionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    java.util.Map.of("locationId", locationId)
                            )))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locationId").value(locationId));
        }

        mockMvc.perform(post("/api/v1/sessions/{id}/explore", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"LIFE_SUPPORT_CONTROL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void discoversCluesForEveryRequiredFrontendObjectHint() throws Exception {
        String sessionId = "backend_object_clues_001";
        mockMvc.perform(post("/internal/v1/cases")
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "backend_object_clues_001",
                                  "seed": "backend-object-clues"
                                }
                                """))
                .andExpect(status().isAccepted());
        awaitState(sessionId, SessionState.READY);

        for (var entry : frontendContracts.contract().investigationObjects().entrySet()) {
            FrontendIntegrationContract.InvestigationObjectRoute route = entry.getValue();
            if (!route.clueRequired()) {
                continue;
            }
            mockMvc.perform(post("/api/v1/sessions/{id}/explore", sessionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "locationId", route.locationId(),
                                    "objectHint", entry.getKey()
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.newlyDiscoveredClues").isNotEmpty());
        }

        mockMvc.perform(post("/api/v1/sessions/{id}/explore", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": "CENTRAL_HUB",
                                  "objectHint": "CO_BODY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void requiresTheSharedInternalKeyForNpcAndRag() throws Exception {
        String sessionId = "backend_internal_auth_001";
        mockMvc.perform(post("/internal/v1/cases")
                        .header("X-Internal-AI-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "backend_internal_auth_001",
                                  "seed": "backend-internal-auth"
                                }
                                """))
                .andExpect(status().isAccepted());
        awaitState(sessionId, SessionState.READY);

        mockMvc.perform(post(
                                "/api/v1/sessions/{id}/assistant/queries",
                                sessionId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "02:05"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_API_KEY"));

        mockMvc.perform(post(
                                "/api/v1/sessions/{id}/interrogations/SOPHIA/turns",
                                sessionId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "기록을 설명해 주세요.",
                                  "presentedClueIds": []
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_API_KEY"));
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
