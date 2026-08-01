package com.arcadia.station.service;

import com.arcadia.station.client.CaseGenerationClient;
import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.config.AiServerProperties;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.repository.GameSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.4절 구간별 백오프 폴링 + 4.5절 자동 재시도 정책. CREATING/VALIDATING 상태로 남아있는
 * 세션을 주기적으로 재확인해 READY로 전환하거나, 통신 실패/타임아웃 시 재시도 후 FAILED로 확정한다.
 * Fake 클라이언트는 세션 생성 시 동기적으로 즉시 BRIEFING까지 끝내므로, 이 워커는 평소엔
 * (real-ai 프로파일이 아닌 한) CREATING/VALIDATING 상태의 세션을 거의 발견하지 못하고 조용히 지나간다.
 */
@Component
public class CaseGenerationPollingWorker {

    private static final Logger log = LoggerFactory.getLogger(CaseGenerationPollingWorker.class);
    private static final List<SessionState> PENDING_STATES = List.of(SessionState.CREATING, SessionState.VALIDATING);
    private static final int MAX_ATTEMPTS = 3; // 최초 1 + 자동 재시도 최대 2 (4.5절)

    private final GameSessionRepository gameSessionRepository;
    private final CaseGenerationClient caseGenerationClient;
    private final AiServerProperties properties;
    private final Random random = new Random();

    public CaseGenerationPollingWorker(
            GameSessionRepository gameSessionRepository,
            CaseGenerationClient caseGenerationClient,
            AiServerProperties properties) {
        this.gameSessionRepository = gameSessionRepository;
        this.caseGenerationClient = caseGenerationClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollPendingSessions() {
        for (GameSession session : gameSessionRepository.findByStateIn(PENDING_STATES)) {
            try {
                processOne(session);
            } catch (Exception e) {
                log.warn("세션 폴링 처리 중 예외 발생: session={}", session.getSessionId(), e);
            }
        }
    }

    private void processOne(GameSession session) {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(session.getCurrentAttemptStartedAt(), now);
        Duration budget = properties.caseGeneration().pollTotalBudget();

        if (elapsed.compareTo(budget) > 0) {
            log.warn("폴링 총 예산({}) 초과, 재시도 정책 적용: session={}", budget, session.getSessionId());
            retryOrFail(session);
            return;
        }

        Duration requiredInterval = backoffInterval(elapsed);
        if (session.getLastPolledAt() != null
                && Duration.between(session.getLastPolledAt(), now).compareTo(requiredInterval) < 0) {
            return;
        }

        try {
            CaseGenerationStatus status = caseGenerationClient.pollStatus(session.getAiCaseRequestId());
            session.setLastPolledAt(now);
            if ("READY".equals(status.status())) {
                GenerationResultApplier.apply(session, status.generation());
            }
            gameSessionRepository.save(session);
        } catch (Exception e) {
            log.warn("AI 서버 폴링 통신 실패, 재시도 정책 적용: session={}", session.getSessionId(), e);
            retryOrFail(session);
        }
    }

    // 0~30초: 2.0초, 30~90초: 3.0초, 90~210초: 5.0초 (+0~300ms 지터), 4.4절
    private Duration backoffInterval(Duration elapsed) {
        long jitterMs = random.nextInt(300);
        long seconds = elapsed.toSeconds();
        long baseMs = seconds <= 30 ? 2000 : (seconds <= 90 ? 3000 : 5000);
        return Duration.ofMillis(baseMs + jitterMs);
    }

    private void retryOrFail(GameSession session) {
        if (session.getCaseRequestAttemptCount() >= MAX_ATTEMPTS) {
            session.setState(SessionState.FAILED);
            gameSessionRepository.save(session);
            return;
        }

        session.setCaseRequestAttemptCount(session.getCaseRequestAttemptCount() + 1);
        String newAiCaseRequestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        session.setAiCaseRequestId(newAiCaseRequestId);
        session.setCurrentAttemptStartedAt(Instant.now());
        session.setLastPolledAt(null);
        try {
            caseGenerationClient.requestCase(newAiCaseRequestId, session.getSeed());
        } catch (Exception e) {
            log.warn("재시도 요청 전송 실패, 다음 스케줄에서 다시 시도: session={}", session.getSessionId(), e);
        }
        gameSessionRepository.save(session);
    }
}
