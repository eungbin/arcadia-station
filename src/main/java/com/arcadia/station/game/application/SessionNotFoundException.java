package com.arcadia.station.game.application;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("Game session not found: " + sessionId);
    }
}
