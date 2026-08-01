package com.arcadia.station.game.application;

import com.arcadia.station.ai.casegen.CaseGenerationService;
import com.arcadia.station.ai.casegen.FrozenCaseBlueprint;
import com.arcadia.station.ai.rag.RagIndexBuilder;
import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.infrastructure.persistence.InMemoryGameSessionRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class GameSessionService {

    private final InMemoryGameSessionRepository sessions;
    private final CaseGenerationService caseGenerationService;
    private final TaskExecutor executor;
    private final RagIndexBuilder ragIndexBuilder;
    private final SecureRandom secureRandom = new SecureRandom();

    public GameSessionService(
            InMemoryGameSessionRepository sessions,
            CaseGenerationService caseGenerationService,
            @Qualifier("caseGenerationExecutor") TaskExecutor executor,
            RagIndexBuilder ragIndexBuilder
    ) {
        this.sessions = sessions;
        this.caseGenerationService = caseGenerationService;
        this.executor = executor;
        this.ragIndexBuilder = ragIndexBuilder;
    }

    public GameSession createSession(String requestedSeed) {
        return createSession(null, requestedSeed);
    }

    public GameSession createSession(String requestedSessionId, String requestedSeed) {
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString()
                : validateSessionId(requestedSessionId);
        String seed = requestedSeed == null || requestedSeed.isBlank()
                ? randomSeed()
                : requestedSeed;
        GameSession session = new GameSession(sessionId, seed);
        sessions.save(session);
        executor.execute(() -> generateCase(session));
        return session;
    }

    public GameSession requireSession(String sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    public FrozenCaseBlueprint requireFrozenCase(String sessionId) {
        GameSession session = requireSession(sessionId);
        if (session.frozenCase() == null) {
            throw new SessionNotReadyException(sessionId, session.state());
        }
        return session.frozenCase();
    }

    private void generateCase(GameSession session) {
        try {
            session.markValidating();
            FrozenCaseBlueprint frozen = caseGenerationService.createCase(
                    session.sessionId(),
                    session.seed()
            );
            session.markReady(frozen);
            ragIndexBuilder.index(frozen);
        } catch (RuntimeException exception) {
            session.fail("CASE_GENERATION_FAILED");
        }
    }

    private String randomSeed() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String validateSessionId(String sessionId) {
        if (!sessionId.matches("[A-Za-z0-9][A-Za-z0-9_-]{7,63}")) {
            throw new IllegalArgumentException(
                    "sessionId must be 8-64 characters using letters, numbers, '_' or '-'"
            );
        }
        return sessionId;
    }
}
