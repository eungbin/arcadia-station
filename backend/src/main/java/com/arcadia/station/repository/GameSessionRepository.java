package com.arcadia.station.repository;

import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, String> {
    List<GameSession> findByStateIn(Collection<SessionState> states);

    // NPC/RAG는 플레이어 sessionId가 아니라 AI 서버 쪽 세션 키(aiCaseRequestId)로 식별한다(AI 서버 회신 3.1절).
    Optional<GameSession> findByAiCaseRequestId(String aiCaseRequestId);
}
