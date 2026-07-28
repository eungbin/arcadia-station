package com.arcadia.station.repository;

import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, String> {
    List<GameSession> findByStateIn(Collection<SessionState> states);
}
