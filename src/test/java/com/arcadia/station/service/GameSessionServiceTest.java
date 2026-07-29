package com.arcadia.station.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcadia.station.client.CaseGenerationClient;
import com.arcadia.station.client.dto.CaseGenerationAck;
import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.dto.response.SessionCreateResponse;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * 3.2절 상태 전이 규칙: 세션 생성 시 Fake 클라이언트가 즉시 READY를 반환하므로
 * CREATING -> VALIDATING -> BRIEFING까지 한 번에 진행되고, 사건 메타데이터가 저장된다.
 */
@SpringBootTest
class GameSessionServiceTest {

    @Autowired
    private GameSessionService gameSessionService;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private EvidenceInventoryRepository evidenceInventoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 최초_시도가_AI_서버와_통신에_실패해도_세션_행은_그대로_저장되어_폴링_워커가_재시도할_수_있다() {
        CaseGenerationClient failingClient = new CaseGenerationClient() {
            @Override
            public CaseGenerationAck requestCase(String aiCaseRequestId, String seed) {
                throw new RuntimeException("연결 실패");
            }

            @Override
            public CaseGenerationStatus pollStatus(String aiCaseRequestId) {
                throw new RuntimeException("연결 실패");
            }
        };
        GameSessionService service = new GameSessionService(
                gameSessionRepository, evidenceInventoryRepository, failingClient, objectMapper);

        SessionCreateResponse response = service.createSession(null);

        assertThat(response.status()).isEqualTo("CREATING");
        GameSession session = gameSessionRepository.findById(response.sessionId()).orElseThrow();
        assertThat(session.getState()).isEqualTo(SessionState.CREATING);
        assertThat(evidenceInventoryRepository.findById(response.sessionId())).isPresent();
    }

    @Test
    void 세션_생성_직후_Fake_클라이언트가_즉시_READY를_반환해_BRIEFING까지_전환된다() {
        SessionCreateResponse response = gameSessionService.createSession(null);

        assertThat(response.status()).isEqualTo("BRIEFING");
        var session = gameSessionRepository.findById(response.sessionId()).orElseThrow();
        assertThat(session.getState()).isEqualTo(SessionState.BRIEFING);
        assertThat(session.getBlueprintId()).isNotBlank();
        assertThat(session.getBlueprintSha256()).isNotBlank();
        assertThat(session.getFrozenAt()).isNotNull();
        assertThat(session.getCaseBlueprintJson()).isNotBlank();
        // 3.1절: 최초 시도이므로 caseRequestAttemptCount는 1
        assertThat(session.getCaseRequestAttemptCount()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_세션의_상태를_조회하면_SESSION_NOT_FOUND다() {
        assertThatThrownBy(() -> gameSessionService.getStatus("game_not_exists"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }
}
