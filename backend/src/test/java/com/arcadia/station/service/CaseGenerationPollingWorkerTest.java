package com.arcadia.station.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.client.CaseGenerationClient;
import com.arcadia.station.client.dto.CaseGenerationAck;
import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.client.dto.GenerationResult;
import com.arcadia.station.config.AiServerProperties;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.repository.GameSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 4.4절 백오프/4.5절 자동 재시도 정책을 실제 AI 서버 없이 검증한다. real-ai 프로파일이 아니어도
 * 워커 자체는 항상 등록되므로, CaseGenerationClient 자리에 손수 만든 테스트 더블을 직접 주입해서 확인한다.
 */
@SpringBootTest
class CaseGenerationPollingWorkerTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private AiServerProperties aiServerProperties;

    @Test
    void 폴링_예산을_초과하면_재시도하고_aiCaseRequestId가_새로_발급된다() {
        GameSession session = seedSession(Instant.now().minus(Duration.ofSeconds(300)));
        String originalAiCaseRequestId = session.getAiCaseRequestId();
        AtomicInteger requestCaseCalls = new AtomicInteger();
        CaseGenerationClient client = testClient(requestCaseCalls, null);

        new CaseGenerationPollingWorker(gameSessionRepository, client, aiServerProperties).pollPendingSessions();

        GameSession reloaded = gameSessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(reloaded.getCaseRequestAttemptCount()).isEqualTo(2);
        assertThat(reloaded.getAiCaseRequestId()).isNotEqualTo(originalAiCaseRequestId);
        assertThat(reloaded.getState()).isEqualTo(SessionState.VALIDATING);
        assertThat(requestCaseCalls.get()).isEqualTo(1);
    }

    @Test
    void 최대_시도횟수에_도달한_뒤_예산을_초과하면_FAILED로_확정된다() {
        GameSession session = seedSession(Instant.now().minus(Duration.ofSeconds(300)));
        session.setCaseRequestAttemptCount(3);
        gameSessionRepository.save(session);
        CaseGenerationClient client = testClient(new AtomicInteger(), null);

        new CaseGenerationPollingWorker(gameSessionRepository, client, aiServerProperties).pollPendingSessions();

        GameSession reloaded = gameSessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(SessionState.FAILED);
    }

    @Test
    void 통신_실패는_예산_만료를_기다리지_않고_즉시_재시도한다() {
        GameSession session = seedSession(Instant.now());
        CaseGenerationClient client = new CaseGenerationClient() {
            @Override
            public CaseGenerationAck requestCase(String aiCaseRequestId, String seed) {
                return new CaseGenerationAck(aiCaseRequestId, "CREATING", "/x");
            }

            @Override
            public CaseGenerationStatus pollStatus(String aiCaseRequestId) {
                throw new RuntimeException("network error");
            }
        };

        new CaseGenerationPollingWorker(gameSessionRepository, client, aiServerProperties).pollPendingSessions();

        GameSession reloaded = gameSessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(reloaded.getCaseRequestAttemptCount()).isEqualTo(2);
    }

    private CaseGenerationClient testClient(AtomicInteger requestCaseCalls, GenerationResult onPollResult) {
        return new CaseGenerationClient() {
            @Override
            public CaseGenerationAck requestCase(String aiCaseRequestId, String seed) {
                requestCaseCalls.incrementAndGet();
                return new CaseGenerationAck(aiCaseRequestId, "CREATING", "/internal/v1/cases/" + aiCaseRequestId);
            }

            @Override
            public CaseGenerationStatus pollStatus(String aiCaseRequestId) {
                String status = onPollResult != null ? "READY" : "VALIDATING";
                return new CaseGenerationStatus(aiCaseRequestId, status, onPollResult, null);
            }
        };
    }

    private GameSession seedSession(Instant currentAttemptStartedAt) {
        String sessionId = "game_test_polling_" + UUID.randomUUID();
        GameSession session = new GameSession(sessionId, "req_test_polling", Instant.now());
        session.setState(SessionState.VALIDATING);
        session.setCurrentAttemptStartedAt(currentAttemptStartedAt);
        gameSessionRepository.save(session);
        return session;
    }
}
