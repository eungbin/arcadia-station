package com.arcadia.station.ai.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arcadia.ai")
public record ArcadiaAiProperties(
        boolean enabled,
        boolean offlineMode,
        String apiKey,
        String model,
        String embeddingModel,
        CaseGeneration caseGeneration,
        Npc npc,
        Rag rag
) {
    public record CaseGeneration(Duration timeout, int maxRetries, String promptVersion) {}

    public record Npc(Duration timeout, int maxHistoryTurns) {}

    public record Rag(int topK, double minimumScore) {}
}
