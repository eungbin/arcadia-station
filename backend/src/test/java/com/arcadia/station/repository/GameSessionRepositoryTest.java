package com.arcadia.station.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * 절대규칙(인메모리 Map 금지)을 지키는지 확인하기 위해 실제 Postgres(docker-compose)에 저장/조회한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GameSessionRepositoryTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Test
    void 세션을_저장하고_조회하면_초기_상태가_CREATING이고_시도횟수는_1이다() {
        GameSession session = new GameSession("game_test_001", "ai_req_001", Instant.now());

        gameSessionRepository.saveAndFlush(session);

        GameSession found = gameSessionRepository.findById("game_test_001").orElseThrow();
        assertThat(found.getState()).isEqualTo(SessionState.CREATING);
        assertThat(found.getCaseRequestAttemptCount()).isEqualTo(1);
        assertThat(found.getAiCaseRequestId()).isEqualTo("ai_req_001");
    }

    @Test
    void 사건_동결_메타데이터를_반영하면_READY_상태로_저장된다() {
        GameSession session = new GameSession("game_test_002", "ai_req_002", Instant.now());
        gameSessionRepository.saveAndFlush(session);

        session.setState(SessionState.READY);
        session.setBlueprintId("case_01ARCADIA");
        session.setBlueprintSha256("911f7af4...81eb");
        session.setGenerationSource("AI");
        session.setGenerationAttemptCount(1);
        session.setFrozenAt(Instant.now());
        session.setCaseBlueprintJson("{\"blueprintId\":\"case_01ARCADIA\"}");
        gameSessionRepository.saveAndFlush(session);

        GameSession found = gameSessionRepository.findById("game_test_002").orElseThrow();
        assertThat(found.getState()).isEqualTo(SessionState.READY);
        assertThat(found.getBlueprintSha256()).isEqualTo("911f7af4...81eb");
        assertThat(found.getFrozenAt()).isNotNull();
        assertThat(found.getCaseBlueprintJson()).contains("case_01ARCADIA");
    }
}
