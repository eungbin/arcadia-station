package com.arcadia.station.infrastructure.persistence;

import com.arcadia.station.game.domain.GameSession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGameSessionRepository {

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public void save(GameSession session) {
        GameSession previous = sessions.putIfAbsent(session.sessionId(), session);
        if (previous != null) {
            throw new IllegalStateException("Duplicate sessionId: " + session.sessionId());
        }
    }

    public Optional<GameSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }
}
