package com.arcadia.station.game.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalApiKeyGuard {

    private final byte[] expectedKey;

    public InternalApiKeyGuard(
            @Value("${arcadia.internal-api-key:}") String expectedKey
    ) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    public void requireValid(String providedKey) {
        if (expectedKey.length == 0) {
            return;
        }
        byte[] provided = providedKey == null
                ? new byte[0]
                : providedKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, provided)) {
            throw new InvalidInternalApiKeyException();
        }
    }
}
