package com.arcadia.station.game.api;

public class InvalidInternalApiKeyException extends RuntimeException {

    public InvalidInternalApiKeyException() {
        super("Invalid internal API key");
    }
}
