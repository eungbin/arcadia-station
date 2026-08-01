package com.arcadia.station.game.application;

import com.arcadia.station.game.domain.SessionState;

public class SessionNotReadyException extends RuntimeException {

    public SessionNotReadyException(String sessionId, SessionState state) {
        super("Game session " + sessionId + " is not ready: " + state);
    }
}
