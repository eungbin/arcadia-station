package com.arcadia.station.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.Clue;
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

@SpringBootTest
class ClueUnlockServiceImplTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private EvidenceInventoryRepository evidenceInventoryRepository;

    @Autowired
    private ClueUnlockService clueUnlockService;

    @Test
    void 위치가_일치하고_선행조건을_만족하면_단서가_해금되고_INVESTIGATION으로_전환된다() throws IOException {
        String sessionId = seedSession();

        List<Clue> unlocked = clueUnlockService.exploreLocation(sessionId, "MEDICAL_BAY", null);

        assertThat(unlocked).extracting(Clue::clueId).containsExactly("CLUE-SETUP-LOG");
        assertThat(gameSessionRepository.findById(sessionId).orElseThrow().getState())
                .isEqualTo(SessionState.INVESTIGATION);
        assertThat(evidenceInventoryRepository.findById(sessionId).orElseThrow().getDiscoveredClueIds())
                .contains("CLUE-SETUP-LOG");
    }

    @Test
    void 선행조건을_충족하지_못한_단서는_해금되지_않는다() throws IOException {
        String sessionId = seedSession();

        // CLUE-ACCESS-HISTORY는 CLUE-TRIGGER-LOG(RAG_QUERY 전용)를 선행조건으로 요구하므로 아직 해금될 수 없다.
        List<Clue> unlocked = clueUnlockService.exploreLocation(sessionId, "ENGINEERING_BAY", null);

        assertThat(unlocked).isEmpty();
    }

    @Test
    void objectHint가_주어지면_해당_오브젝트의_단서만_해금된다() throws IOException {
        String sessionId = seedSession();

        List<Clue> unlocked = clueUnlockService.exploreLocation(sessionId, "MEDICAL_BAY", "MD_MEDICAL_TERMINAL");

        assertThat(unlocked).extracting(Clue::clueId).containsExactly("CLUE-SETUP-LOG");
    }

    @Test
    void objectHint가_해당_장소의_단서와_맞지_않으면_빈_목록을_반환한다() throws IOException {
        String sessionId = seedSession();

        List<Clue> unlocked = clueUnlockService.exploreLocation(sessionId, "MEDICAL_BAY", "MD_MEDICAL_STORAGE");

        assertThat(unlocked).isEmpty();
    }

    @Test
    void 필요한_단서를_모두_보유하면_CONNECT_단서가_연쇄_해금된다() throws IOException {
        String sessionId = seedSession();
        EvidenceInventory inventory = evidenceInventoryRepository.findById(sessionId).orElseThrow();
        inventory.getDiscoveredClueIds().add("CLUE-SETUP-LOG");
        inventory.getDiscoveredClueIds().add("CLUE-ACCESS-HISTORY");
        evidenceInventoryRepository.save(inventory);

        List<Clue> unlocked = clueUnlockService.resolveConnectClues(sessionId);

        assertThat(unlocked).extracting(Clue::clueId).containsExactly("CLUE-FULL-PICTURE");
    }

    private String seedSession() throws IOException {
        String sessionId = "game_test_explore_" + UUID.randomUUID();
        GameSession session = new GameSession(sessionId, "req_test_explore", Instant.now());
        session.setState(SessionState.BRIEFING);
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
