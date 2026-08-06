package com.arcadia.station.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.dto.response.PlayerCaseView;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 10장 보안 경계 회귀 테스트: PlayerCaseView(및 그 안의 단서 목록)를 직렬화한 JSON에
 * 스포일러 필드(culpritId, truthSummary, method, solution, actualWhereabouts,
 * concealedFactIds, allowedLieFactIds, resolutionFactIds)와 단서 내부 판정 필드
 * (revealsFactIds, solutionRoles, truthValue)가 절대 나타나지 않는지 고정한다.
 *
 * suspectEffects는 요청 A(FRONTEND_BACKEND_CLUE_CONTEXT_REQUEST_2026-08-06.md 3.2절)에 따라
 * 의도적으로 노출하는 필드라 금지 목록에서 제외했다 — playerText로도 어차피 드러나는 수준이라 스포일러가 아니다.
 */
@SpringBootTest
class PlayerCaseViewSerializationTest {

    private static final List<String> FORBIDDEN_KEYS = List.of(
            "\"culpritId\"",
            "\"truthSummary\"",
            "\"method\"",
            "\"solution\"",
            "\"actualWhereabouts\"",
            "\"concealedFactIds\"",
            "\"allowedLieFactIds\"",
            "\"resolutionFactIds\"",
            "\"revealsFactIds\"",
            "\"solutionRoles\"",
            "\"truthValue\"",
            "\"npcKnowledge\"",
            "\"redHerrings\"",
            "\"alibis\"",
            "\"facts\"",
            "\"timeline\"",
            "\"evidenceRecords\"");

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private EvidenceInventoryRepository evidenceInventoryRepository;

    @Autowired
    private GameSessionService gameSessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 아무_단서도_발견하지_않은_상태에서도_스포일러_필드가_없다() throws IOException {
        String sessionId = seedSession(List.of());

        String json = objectMapper.writeValueAsString(gameSessionService.getPlayerView(sessionId));

        assertNoForbiddenKeys(json);
        assertThat(json).contains("\"discoveredClues\":[]");
    }

    @Test
    void 단서를_일부_발견해도_스포일러_필드가_없고_공개용_필드만_노출된다() throws IOException {
        String sessionId = seedSession(List.of("CLUE-SETUP-LOG", "CLUE-TRIGGER-LOG"));

        PlayerCaseView view = gameSessionService.getPlayerView(sessionId);
        String json = objectMapper.writeValueAsString(view);

        assertNoForbiddenKeys(json);
        assertThat(view.discoveredClues()).hasSize(2);
        // 발견한 단서는 clueId/title/clueType/playerText만 노출되어야 한다.
        assertThat(json).contains("\"playerText\"");
        // 아직 발견하지 않은 CLUE-MOTIVE-MESSAGE, CLUE-ACCESS-HISTORY의 playerText는 노출되면 안 된다.
        assertThat(json).doesNotContain("소피아가 피해자로부터 해고 통보를 받았다는 개인 메시지가 남아있다.");
        assertThat(json).doesNotContain("02:03 소피아가 생명유지구역에 출입한 기록이 있다.");
    }

    private void assertNoForbiddenKeys(String json) {
        for (String key : FORBIDDEN_KEYS) {
            assertThat(json).doesNotContain(key);
        }
    }

    private String seedSession(List<String> discoveredClueIds) throws IOException {
        String sessionId = "game_test_playerview_" + UUID.randomUUID();
        GameSession session = new GameSession(sessionId, "req_test_playerview", Instant.now());
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
