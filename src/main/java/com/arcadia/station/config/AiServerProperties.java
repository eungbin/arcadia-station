package com.arcadia.station.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 12장 설정값(arcadia.ai-server.*)을 그대로 매핑한다.
 */
@ConfigurationProperties(prefix = "arcadia.ai-server")
public record AiServerProperties(
    String baseUrl,
    String internalApiKey,
    CaseGeneration caseGeneration,
    Timeout interrogation,
    Timeout assistant
) {
    public record CaseGeneration(
        Duration connectTimeout, Duration perAttemptTimeout, Duration pollTotalBudget, int maxAutoRetry) {}

    public record Timeout(Duration timeout) {}
}
