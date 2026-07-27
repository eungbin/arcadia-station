package com.arcadia.station.game.application;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.game.api.dto.FinalCaseReveal;
import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.game.domain.SessionState;
import org.springframework.stereotype.Service;

@Service
public class FinalRevealService {

    private final GameSessionService sessions;

    public FinalRevealService(GameSessionService sessions) {
        this.sessions = sessions;
    }

    public FinalCaseReveal get(String sessionId) {
        GameSession session = sessions.requireSession(sessionId);
        if (session.state() != SessionState.COMPLETED) {
            throw new IllegalStateException("Final reveal is available only after completion");
        }
        CaseBlueprint blueprint = sessions.requireFrozenCase(sessionId).blueprint();
        return new FinalCaseReveal(
                sessionId,
                blueprint.title(),
                blueprint.culpritId(),
                blueprint.truthSummary(),
                blueprint.method(),
                blueprint.timeline(),
                blueprint.solution().nonCulpritExclusions()
        );
    }
}
