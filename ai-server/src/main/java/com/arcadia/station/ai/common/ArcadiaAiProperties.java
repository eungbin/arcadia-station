package com.arcadia.station.ai.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arcadia.ai")
public record ArcadiaAiProperties(
        boolean enabled,
        boolean offlineMode,
        Provider provider,
        ProviderSettings openai,
        ProviderSettings gemini,
        Duration quotaCooldown,
        CaseGeneration caseGeneration,
        Npc npc,
        Rag rag
) {
    public enum Provider {
        OPENAI,
        GEMINI
    }

    public record ProviderSettings(
            String apiKey,
            String model,
            String embeddingModel,
            String baseUrl
    ) {}

    public record CaseGeneration(Duration timeout, int maxRetries, String promptVersion) {}

    public record Npc(Duration timeout, int maxHistoryTurns) {}

    public record Rag(int topK, double minimumScore) {}

    public Provider activeProvider() {
        return provider == null ? Provider.OPENAI : provider;
    }

    public ProviderSettings activeSettings() {
        return activeProvider() == Provider.GEMINI ? gemini : openai;
    }

    public boolean hasActiveApiKey() {
        ProviderSettings settings = activeSettings();
        return settings != null
                && settings.apiKey() != null
                && !settings.apiKey().isBlank();
    }

    public String activeModel() {
        ProviderSettings settings = activeSettings();
        return settings == null ? "offline-fallback" : settings.model();
    }
}
