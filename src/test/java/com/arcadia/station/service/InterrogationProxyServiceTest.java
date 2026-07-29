package com.arcadia.station.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcadia.station.client.AiSessionLostException;
import com.arcadia.station.client.InterrogationClient;
import com.arcadia.station.client.dto.NpcTurnResult;
import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.dto.response.NpcTurnResponse;
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
 * "AI 서버 응답을 그대로 믿지 않는다"(5.3절) 재검증 로직을 AI 클라이언트 종류와 무관하게 검증한다.
 * FakeInterrogationClient는 항상 선의로 동작하므로, 여기서는 일부러 화이트리스트를 벗어나거나
 * 세션을 잃어버린 것처럼 구는 테스트 전용 클라이언트를 직접 주입해서 방어 로직 자체를 확인한다.
 */
@SpringBootTest
class InterrogationProxyServiceTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private EvidenceInventoryRepository evidenceInventoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 허용된_revealPolicy_범위_안의_사실만_공개하면_그대로_반영된다() throws IOException {
        String sessionId = seedSession(List.of("CLUE-TRIGGER-LOG"));
        InterrogationProxyService service = newService(
                (sid, characterId, question, presentedClueIds) ->
                        new NpcTurnResult("인정합니다.", "DEFENSIVE", List.of("FACT-TRIGGER"), List.of()));

        NpcTurnResponse response = service.ask(sessionId, "SOPHIA", "질문", List.of("CLUE-TRIGGER-LOG"));

        assertThat(response.revealedFactIds()).containsExactly("FACT-TRIGGER");
        assertThat(evidenceInventoryRepository.findById(sessionId).orElseThrow().getRevealedFactIds())
                .contains("FACT-TRIGGER");
    }

    @Test
    void 제시하지_않은_단서에_대한_사실을_AI가_공개했다고_주장하면_전체를_빈_배열로_덮어쓴다() throws IOException {
        String sessionId = seedSession(List.of("CLUE-TRIGGER-LOG"));
        // FACT-MOTIVE는 CLUE-MOTIVE-MESSAGE를 제시해야만 허용되는데, 이번 턴엔 제시하지 않았다.
        InterrogationClient overclaimingClient = (sid, characterId, question, presentedClueIds) ->
                new NpcTurnResult("사실은요...", "DEFENSIVE", List.of("FACT-TRIGGER", "FACT-MOTIVE"), List.of());
        InterrogationProxyService service = newService(overclaimingClient);

        NpcTurnResponse response = service.ask(sessionId, "SOPHIA", "질문", List.of("CLUE-TRIGGER-LOG"));

        assertThat(response.revealedFactIds()).isEmpty();
        assertThat(response.dialogue()).isEqualTo("사실은요...");
        assertThat(evidenceInventoryRepository.findById(sessionId).orElseThrow().getRevealedFactIds())
                .doesNotContain("FACT-MOTIVE");
    }

    @Test
    void 이전_턴에_제시했던_단서는_이번_턴에_다시_제시하지_않아도_화이트리스트에_누적으로_반영된다() throws IOException {
        String sessionId = seedSession(List.of("CLUE-TRIGGER-LOG"));
        InterrogationProxyService service = newService(
                (sid, characterId, question, presentedClueIds) ->
                        new NpcTurnResult("...", "DEFENSIVE", List.of("FACT-TRIGGER"), List.of()));

        // 1턴: CLUE-TRIGGER-LOG를 제시해서 FACT-TRIGGER를 공개
        service.ask(sessionId, "SOPHIA", "질문1", List.of("CLUE-TRIGGER-LOG"));
        // 2턴: 아무 단서도 다시 제시하지 않음 — 그래도 이미 제시했던 CLUE-TRIGGER-LOG 기준으로 여전히 허용돼야 한다.
        NpcTurnResponse response = service.ask(sessionId, "SOPHIA", "질문2", List.of());

        assertThat(response.revealedFactIds()).containsExactly("FACT-TRIGGER");
    }

    @Test
    void AI_클라이언트에는_플레이어_sessionId가_아니라_aiCaseRequestId를_전달한다() throws IOException {
        String sessionId = seedSession(List.of("CLUE-TRIGGER-LOG"));
        AtomicReference<String> capturedId = new AtomicReference<>();
        InterrogationProxyService service = newService((sid, characterId, question, presentedClueIds) -> {
            capturedId.set(sid);
            return new NpcTurnResult("x", "CALM", List.of(), List.of());
        });

        service.ask(sessionId, "SOPHIA", "질문", List.of("CLUE-TRIGGER-LOG"));

        assertThat(capturedId.get()).isEqualTo("req_test_interrogation").isNotEqualTo(sessionId);
    }

    @Test
    void AI_클라이언트에는_이번_턴과_이전_턴을_합친_누적_제시목록을_전달한다() throws IOException {
        String sessionId = seedSession(List.of("CLUE-TRIGGER-LOG", "CLUE-MOTIVE-MESSAGE"));
        AtomicReference<List<String>> capturedPresented = new AtomicReference<>();
        InterrogationProxyService service = newService((sid, characterId, question, presentedClueIds) -> {
            capturedPresented.set(presentedClueIds);
            return new NpcTurnResult("x", "CALM", List.of(), List.of());
        });

        service.ask(sessionId, "SOPHIA", "질문1", List.of("CLUE-TRIGGER-LOG"));
        service.ask(sessionId, "SOPHIA", "질문2", List.of("CLUE-MOTIVE-MESSAGE"));

        assertThat(capturedPresented.get()).containsExactlyInAnyOrder("CLUE-TRIGGER-LOG", "CLUE-MOTIVE-MESSAGE");
    }

    @Test
    void 빈_질문은_INVALID_REQUEST다() throws IOException {
        String sessionId = seedSession(List.of());
        InterrogationProxyService service = newService(
                (sid, characterId, question, presentedClueIds) -> new NpcTurnResult("x", "CALM", List.of(), List.of()));

        assertThatThrownBy(() -> service.ask(sessionId, "SOPHIA", "   ", List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void AI_서버가_세션을_잃어버리면_안전응답으로_대체하고_플래그를_남긴다() throws IOException {
        String sessionId = seedSession(List.of());
        InterrogationClient lostClient = (sid, characterId, question, presentedClueIds) -> {
            throw new AiSessionLostException("session lost");
        };
        InterrogationProxyService service = newService(lostClient);

        NpcTurnResponse response = service.ask(sessionId, "SOPHIA", "질문", List.of());

        assertThat(response.revealedFactIds()).isEmpty();
        assertThat(response.dialogue()).contains("잠시 후");
        assertThat(gameSessionRepository.findById(sessionId).orElseThrow().isAiSessionLost()).isTrue();
    }

    @Test
    void 미발견_단서를_제시하면_INVALID_REQUEST를_던진다() throws IOException {
        String sessionId = seedSession(List.of());
        InterrogationProxyService service = newService(
                (sid, characterId, question, presentedClueIds) -> new NpcTurnResult("x", "CALM", List.of(), List.of()));

        assertThatThrownBy(() -> service.ask(sessionId, "SOPHIA", "질문", List.of("CLUE-TRIGGER-LOG")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private InterrogationProxyService newService(InterrogationClient client) {
        return new InterrogationProxyService(gameSessionRepository, evidenceInventoryRepository, client, objectMapper);
    }

    private String seedSession(List<String> discoveredClueIds) throws IOException {
        String sessionId = "game_test_interrogation_" + UUID.randomUUID();
        GameSession session = new GameSession(sessionId, "req_test_interrogation", Instant.now());
        session.setState(SessionState.INVESTIGATION);
        session.setCaseBlueprintJson(readFixture());
        gameSessionRepository.save(session);

        EvidenceInventory inventory = new EvidenceInventory(sessionId);
        inventory.getDiscoveredClueIds().addAll(discoveredClueIds);
        evidenceInventoryRepository.save(inventory);
        return sessionId;
    }

    private String readFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/sample-case-blueprint.json")) {
            return new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
        }
    }
}
