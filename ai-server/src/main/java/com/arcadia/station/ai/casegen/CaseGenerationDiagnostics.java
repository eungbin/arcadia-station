package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.validation.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CaseGenerationDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseGenerationDiagnostics.class);
    private static final int MAX_LOGGED_CLUES = 50;

    private final ArcadiaAiProperties properties;
    private final ObjectMapper objectMapper;

    public CaseGenerationDiagnostics(
            ArcadiaAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void generationStarted(
            String sessionId,
            int maximumAttempts,
            boolean externalAiEnabled
    ) {
        safely("case_generation_started", () -> LOGGER.info(
                "event=case_generation_started sessionId={} provider={} configuredModel={} "
                        + "promptVersion={} maxAttempts={} aiEnabled={} offlineMode={} "
                        + "apiKeyConfigured={} externalAiEnabled={}",
                sessionId,
                properties.activeProvider(),
                safeValue(properties.activeModel()),
                safeValue(properties.caseGeneration().promptVersion()),
                maximumAttempts,
                properties.enabled(),
                properties.offlineMode(),
                properties.hasActiveApiKey(),
                externalAiEnabled
        ));
    }

    public void attemptStarted(
            String sessionId,
            int attempt,
            int maximumAttempts,
            List<ValidationIssue> previousIssues
    ) {
        safely("case_generation_attempt_started", () -> LOGGER.info(
                "event=case_generation_attempt_started sessionId={} attempt={} maxAttempts={} "
                        + "previousValidationCodes={}",
                sessionId,
                attempt,
                maximumAttempts,
                validationCodesJson(previousIssues)
        ));
    }

    public void attemptRejected(
            String sessionId,
            int attempt,
            List<ValidationIssue> issues
    ) {
        safely("case_generation_attempt_failed", () -> LOGGER.warn(
                "event=case_generation_attempt_failed sessionId={} attempt={} "
                        + "reason=VALIDATION_FAILED validationCodes={}",
                sessionId,
                attempt,
                validationCodesJson(issues)
        ));
    }

    public void attemptFailed(
            String sessionId,
            int attempt,
            CaseGenerationFallbackReason reason,
            RuntimeException exception
    ) {
        safely("case_generation_attempt_failed", () -> LOGGER.warn(
                "event=case_generation_attempt_failed sessionId={} attempt={} reason={} "
                        + "exceptionType={} rootCauseType={}",
                sessionId,
                attempt,
                reason,
                exception.getClass().getSimpleName(),
                rootCauseType(exception)
        ));
    }

    public void generationCompleted(
            FrozenCaseBlueprint frozen,
            CaseGenerationFallbackReason fallbackReason,
            boolean aiPathAttempted,
            Instant startedAt
    ) {
        safely("case_generation_completed", () -> logGenerationCompleted(
                frozen,
                fallbackReason,
                aiPathAttempted,
                startedAt
        ));
    }

    private void logGenerationCompleted(
            FrozenCaseBlueprint frozen,
            CaseGenerationFallbackReason fallbackReason,
            boolean aiPathAttempted,
            Instant startedAt
    ) {
        List<CaseBlueprint.Clue> clues = frozen.blueprint().clues();
        List<CaseBlueprint.Clue> sortedClues = clues.stream()
                .sorted(Comparator.comparing(CaseBlueprint.Clue::clueId))
                .toList();
        List<CaseBlueprint.Clue> loggedClues = sortedClues.stream()
                .limit(MAX_LOGGED_CLUES)
                .toList();
        boolean clueManifestTruncated = clues.size() > loggedClues.size();
        long exploreClueCount = clues.stream()
                .filter(clue -> clue.acquisition().type()
                        == CaseBlueprint.AcquisitionType.EXPLORE)
                .count();
        long objectClueCount = clues.stream()
                .filter(clue -> clue.acquisition().type()
                        == CaseBlueprint.AcquisitionType.EXPLORE)
                .filter(clue -> "PHYSICAL_OBJECT".equals(clue.source().sourceType()))
                .count();
        String clueSetSha256 = clueSetSha256(clues);
        String clueIdsJson = writeJson(loggedClues.stream()
                .map(CaseBlueprint.Clue::clueId)
                .map(this::safeValue)
                .toList());
        String manifestJson = writeJson(loggedClues.stream()
                .map(this::toClueLogView)
                .toList());
        long durationMillis = Math.max(
                0,
                Duration.between(startedAt, frozen.frozenAt()).toMillis()
        );

        LOGGER.info(
                "event=case_generation_completed sessionId={} generationSource={} "
                        + "fallbackReason={} aiPathAttempted={} attempts={} durationMs={} "
                        + "provider={} configuredModel={} promptVersion={} blueprintId={} "
                        + "blueprintSha256={} clueSetSha256={} clueCount={} "
                        + "exploreClueCount={} objectClueCount={} loggedClueCount={} "
                        + "clueManifestTruncated={} clueIds={}",
                frozen.sessionId(),
                frozen.generationSource(),
                fallbackReason,
                aiPathAttempted,
                frozen.generationAttemptCount(),
                durationMillis,
                properties.activeProvider(),
                safeValue(frozen.model()),
                safeValue(frozen.promptVersion()),
                safeValue(frozen.blueprintId()),
                frozen.blueprintSha256(),
                clueSetSha256,
                clues.size(),
                exploreClueCount,
                objectClueCount,
                loggedClues.size(),
                clueManifestTruncated,
                clueIdsJson
        );
        LOGGER.info(
                "event=case_generation_clues sessionId={} clueSetSha256={} "
                        + "clueManifestTruncated={} clues={}",
                frozen.sessionId(),
                clueSetSha256,
                clueManifestTruncated,
                manifestJson
        );
    }

    String clueSetSha256(List<CaseBlueprint.Clue> clues) {
        try {
            List<CaseBlueprint.Clue> sorted = clues.stream()
                    .sorted(Comparator.comparing(CaseBlueprint.Clue::clueId))
                    .toList();
            byte[] json = objectMapper.writeValueAsBytes(sorted);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            LOGGER.warn(
                    "event=case_generation_log_fingerprint_failed exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            return "unavailable";
        }
    }

    private String validationCodesJson(List<ValidationIssue> issues) {
        return writeJson(issues.stream()
                .map(ValidationIssue::code)
                .distinct()
                .sorted()
                .toList());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            LOGGER.warn(
                    "event=case_generation_log_serialization_failed exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            return "[]";
        }
    }

    private String safeValue(String value) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_');
        return normalized.substring(0, Math.min(normalized.length(), 128));
    }

    private String rootCauseType(Throwable exception) {
        Throwable current = exception;
        int remainingDepth = 20;
        while (remainingDepth-- > 0
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private ClueLogView toClueLogView(CaseBlueprint.Clue clue) {
        return new ClueLogView(
                safeValue(clue.clueId()),
                safeValue(clue.title()),
                clue.clueType(),
                clue.isCore(),
                safeValue(clue.source().sourceType()),
                safeValue(clue.source().sourceId()),
                clue.acquisition().type(),
                clue.acquisition().locationId() == null
                        ? null
                        : safeValue(clue.acquisition().locationId())
        );
    }

    private void safely(String event, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            try {
                LOGGER.warn(
                        "event=case_generation_diagnostics_failed diagnosticEvent={} "
                                + "exceptionType={}",
                        event,
                        exception.getClass().getSimpleName()
                );
            } catch (RuntimeException ignored) {
                // Diagnostics must never affect case generation.
            }
        }
    }

    private record ClueLogView(
            String clueId,
            String title,
            CaseBlueprint.ClueType clueType,
            boolean core,
            String sourceType,
            String sourceId,
            CaseBlueprint.AcquisitionType acquisitionType,
            String locationId
    ) {}
}
