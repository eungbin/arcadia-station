package com.arcadia.station.ai.casegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SessionCaseFreezer {

    private final ObjectMapper objectMapper;

    public SessionCaseFreezer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FrozenCaseBlueprint freeze(
            String sessionId,
            CaseBlueprint blueprint,
            int attempts,
            GenerationSource source,
            String model,
            String promptVersion,
            Instant createdAt
    ) {
        try {
            String json = objectMapper.writeValueAsString(blueprint);
            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(json.getBytes(StandardCharsets.UTF_8))
            );
            return new FrozenCaseBlueprint(
                    sessionId,
                    blueprint.blueprintId(),
                    blueprint.worldTemplate().id(),
                    blueprint.worldTemplate().version(),
                    blueprint.ruleTemplate().id(),
                    blueprint.ruleTemplate().version(),
                    blueprint.seed(),
                    blueprint,
                    json,
                    hash,
                    attempts,
                    source,
                    model,
                    promptVersion,
                    createdAt,
                    Instant.now()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot freeze CaseBlueprint", exception);
        }
    }
}
