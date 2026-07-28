package com.arcadia.station.repository;

import com.arcadia.station.domain.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, String> {
}
