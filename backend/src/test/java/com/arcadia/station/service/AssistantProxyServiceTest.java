package com.arcadia.station.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcadia.station.client.AiSessionLostException;
import com.arcadia.station.client.AiTurnFailedException;
import com.arcadia.station.client.AssistantClient;
import com.arcadia.station.client.dto.AssistantQueryResult;
import com.arcadia.station.client.dto.RagDiscoveredClueRef;
import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.dto.response.AssistantQueryResponse;
import com.arcadia.station.dto.response.PlayerClueView;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 6.2절 재검증(citedRecordIds/newlyDiscoveredClues)이 AI 클라이언트 응답 내용과 무관하게 항상 동작하는지 확인한다.
 */
@SpringBootTest
class AssistantProxyServiceTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private EvidenceInventoryRepository evidenceInventoryRepository;

    @Autowired
    private ClueUnlockService clueUnlockService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 유효한_인용과_RAG_QUERY_단서는_그대로_반영된다() throws IOException {
        String sessionId = seedSession();
        AssistantClient client = (sid, question) -> new AssistantQueryResult(
                "02:05 안전 진단 기록",
                List.of("RECORD-TRIGGER"),
                List.of("추가 질문1", "추가 질문2"),
                List.of(new RagDiscoveredClueRef("CLUE-TRIGGER-LOG")));

        AssistantQueryResponse response = newService(client).query(sessionId, "02:05 안전 진단");

        assertThat(response.citedRecordIds()).containsExactly("RECORD-TRIGGER");
        assertThat(response.newlyDiscoveredClues()).extracting(PlayerClueView::clueId).containsExactly("CLUE-TRIGGER-LOG");
        assertThat(evidenceInventoryRepository.findById(sessionId).orElseThrow().getDiscoveredClueIds())
                .contains("CLUE-TRIGGER-LOG");
    }

    @Test
    void 존재하지_않는_recordId와_RAG_QUERY가_아닌_clueId는_걸러진다() throws IOException {
        String sessionId = seedSession();
        AssistantClient client = (sid, question) -> new AssistantQueryResult(
                "그럴듯한 답변",
                List.of("RECORD-NOT-EXIST"),
                List.of("추가 질문1", "추가 질문2"),
                // CLUE-SETUP-LOG는 EXPLORE 타입이라 RAG로 해금될 수 없다.
                List.of(new RagDiscoveredClueRef("CLUE-SETUP-LOG")));

        AssistantQueryResponse response = newService(client).query(sessionId, "아무 질문");

        assertThat(response.citedRecordIds()).isEmpty();
        assertThat(response.newlyDiscoveredClues()).isEmpty();
        assertThat(evidenceInventoryRepository.findById(sessionId).orElseThrow().getDiscoveredClueIds())
                .doesNotContain("CLUE-SETUP-LOG");
    }

    @Test
    void AI_클라이언트에는_플레이어_sessionId가_아니라_aiCaseRequestId를_전달한다() throws IOException {
        String sessionId = seedSession();
        AtomicReference<String> capturedId = new AtomicReference<>();
        AssistantClient client = (sid, question) -> {
            capturedId.set(sid);
            return new AssistantQueryResult("x", List.of(), List.of(), List.of());
        };

        newService(client).query(sessionId, "질문");

        assertThat(capturedId.get()).isEqualTo("req_test_assistant").isNotEqualTo(sessionId);
    }

    @Test
    void 빈_질문은_INVALID_REQUEST다() throws IOException {
        String sessionId = seedSession();
        AssistantClient client = (sid, question) -> new AssistantQueryResult("x", List.of(), List.of(), List.of());

        assertThatThrownBy(() -> newService(client).query(sessionId, ""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void AI_서버가_세션을_잃어버리면_안전응답으로_대체하고_플래그를_남긴다() throws IOException {
        String sessionId = seedSession();
        AssistantClient lostClient = (sid, question) -> {
            throw new AiSessionLostException("session lost");
        };

        AssistantQueryResponse response = newService(lostClient).query(sessionId, "질문");

        assertThat(response.answer()).contains("잠시 후");
        assertThat(response.citedRecordIds()).isEmpty();
        assertThat(gameSessionRepository.findById(sessionId).orElseThrow().isAiSessionLost()).isTrue();
    }

    @Test
    void AI_서버가_404가_아닌_이유로_실패해도_게임을_중단시키지_않고_플래그는_남기지_않는다() throws IOException {
        String sessionId = seedSession();
        AssistantClient failingClient = (sid, question) -> {
            throw new AiTurnFailedException("AI 서버 400", new RuntimeException("Bad Request"));
        };

        AssistantQueryResponse response = newService(failingClient).query(sessionId, "질문");

        assertThat(response.answer()).contains("잠시 후");
        assertThat(response.citedRecordIds()).isEmpty();
        assertThat(gameSessionRepository.findById(sessionId).orElseThrow().isAiSessionLost()).isFalse();
    }

    private AssistantProxyService newService(AssistantClient client) {
        return new AssistantProxyService(
                gameSessionRepository, evidenceInventoryRepository, client, clueUnlockService, objectMapper);
    }

    private String seedSession() throws IOException {
        String sessionId = "game_test_assistant_" + UUID.randomUUID();
        GameSession session = new GameSession(sessionId, "req_test_assistant", Instant.now());
        session.setState(SessionState.INVESTIGATION);
        session.setCaseBlueprintJson(readFixture());
        gameSessionRepository.save(session);
        evidenceInventoryRepository.save(new EvidenceInventory(sessionId));
        return sessionId;
    }

    private String readFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/sample-case-blueprint.json")) {
            return new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
        }
    }
}
