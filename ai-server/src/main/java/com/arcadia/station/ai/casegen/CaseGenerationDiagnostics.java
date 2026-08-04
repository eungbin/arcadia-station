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

    private static final Logger LOGGER = LoggerFactory.getLogger("ARC_AI_CASE_AUDIT");
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
            boolean externalAiEnabled,
            CaseGenerationFallbackReason configurationReason
    ) {
        safely("case_generation_started", () -> LOGGER.info(
                "event=case_generation_started sessionId={} configuredProvider={} "
                        + "configuredModel={} "
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
        safely("ai_case_start", () -> LOGGER.info(
                "[AI-CASE][START] event=ai_case_start sessionId={} configuredMode={} "
                        + "fallbackReason={} configuredProvider={} configuredModel={} "
                        + "maxAttempts={} aiEnabled={} offlineMode={} apiKeyConfigured={}",
                sessionId,
                externalAiEnabled ? "API" : "FALLBACK",
                configurationReason,
                properties.activeProvider(),
                safeValue(properties.activeModel()),
                maximumAttempts,
                properties.enabled(),
                properties.offlineMode(),
                properties.hasActiveApiKey()
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
                .map(value -> safeGeneratedValue(value, frozen.seed()))
                .toList());
        String manifestJson = writeJson(loggedClues.stream()
                .map(clue -> toClueLogView(clue, frozen.seed()))
                .toList());
        long durationMillis = Math.max(
                0,
                Duration.between(startedAt, frozen.frozenAt()).toMillis()
        );

        LOGGER.info(
                "event=case_generation_completed sessionId={} generationSource={} "
                        + "fallbackReason={} aiPathAttempted={} attempts={} durationMs={} "
                        + "configuredProvider={} configuredModel={} promptVersion={} "
                        + "blueprintId={} "
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
                safeGeneratedValue(frozen.blueprintId(), frozen.seed()),
                frozen.blueprintSha256(),
                clueSetSha256,
                clues.size(),
                exploreClueCount,
                objectClueCount,
                loggedClues.size(),
                clueManifestTruncated,
                clueIdsJson
        );
        LOGGER.debug(
                "event=case_generation_clues sessionId={} clueSetSha256={} "
                        + "clueManifestTruncated={} clues={}",
                frozen.sessionId(),
                clueSetSha256,
                clueManifestTruncated,
                manifestJson
        );
        logReadableClueList(
                frozen,
                fallbackReason,
                aiPathAttempted,
                loggedClues,
                clueSetSha256,
                clueManifestTruncated
        );
    }

    private void logReadableClueList(
            FrozenCaseBlueprint frozen,
            CaseGenerationFallbackReason fallbackReason,
            boolean aiPathAttempted,
            List<CaseBlueprint.Clue> loggedClues,
            String clueSetSha256,
            boolean clueManifestTruncated
    ) {
        String mode = frozen.generationSource() == GenerationSource.AI
                ? "API"
                : "FALLBACK";
        int totalClueCount = frozen.blueprint().clues().size();
        LOGGER.info(
                "[AI-CASE][RESULT] event=ai_case_result sessionId={} mode={} "
                        + "generationSource={} fallbackReason={} aiPathAttempted={} "
                        + "configuredProvider={} configuredModel={} attempts={} "
                        + "clueCount={} clueSetSha256={}",
                frozen.sessionId(),
                mode,
                frozen.generationSource(),
                fallbackReason,
                aiPathAttempted,
                properties.activeProvider(),
                safeGeneratedValue(frozen.model(), frozen.seed()),
                frozen.generationAttemptCount(),
                totalClueCount,
                clueSetSha256
        );
        for (int index = 0; index < loggedClues.size(); index++) {
            CaseBlueprint.Clue clue = loggedClues.get(index);
            LOGGER.info(
                    "[AI-CASE][CLUE] event=ai_case_clue sessionId={} number={}/{} "
                            + "clueId={} title=\"{}\" clueType={} core={} "
                            + "acquisitionType={} locationId={} sourceType={} sourceId={}",
                    frozen.sessionId(),
                    index + 1,
                    totalClueCount,
                    safeGeneratedLogValue(clue.clueId(), frozen.seed()),
                    safeGeneratedLogValue(clue.title(), frozen.seed()),
                    clue.clueType(),
                    clue.isCore(),
                    clue.acquisition().type(),
                    safeGeneratedLogValue(
                            clue.acquisition().locationId(),
                            frozen.seed()
                    ),
                    safeGeneratedLogValue(clue.source().sourceType(), frozen.seed()),
                    safeGeneratedLogValue(clue.source().sourceId(), frozen.seed())
            );
        }
        LOGGER.info(
                "[AI-CASE][END] event=ai_case_clue_list_complete sessionId={} mode={} "
                        + "loggedClueCount={} totalClueCount={} clueManifestTruncated={}",
                frozen.sessionId(),
                mode,
                loggedClues.size(),
                totalClueCount,
                clueManifestTruncated
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
        StringBuilder normalized = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            int characterType = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || characterType == Character.FORMAT
                    || characterType == Character.LINE_SEPARATOR
                    || characterType == Character.PARAGRAPH_SEPARATOR) {
                normalized.append('_');
            } else {
                normalized.appendCodePoint(codePoint);
            }
        });
        String normalizedValue = normalized.toString();
        int codePointCount = normalizedValue.codePointCount(0, normalizedValue.length());
        int endIndex = normalizedValue.offsetByCodePoints(
                0,
                Math.min(codePointCount, 128)
        );
        return normalizedValue.substring(0, endIndex);
    }

    private String safeGeneratedValue(String value, String seed) {
        if (value == null) {
            return safeValue(null);
        }
        String redacted = seed == null || seed.isBlank()
                ? value
                : value.replace(seed, "[REDACTED_SEED]");
        return safeValue(redacted);
    }

    private String safeGeneratedLogValue(String value, String seed) {
        return safeGeneratedValue(value, seed)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
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

    private ClueLogView toClueLogView(CaseBlueprint.Clue clue, String seed) {
        return new ClueLogView(
                safeGeneratedValue(clue.clueId(), seed),
                safeGeneratedValue(clue.title(), seed),
                clue.clueType(),
                clue.isCore(),
                safeGeneratedValue(clue.source().sourceType(), seed),
                safeGeneratedValue(clue.source().sourceId(), seed),
                clue.acquisition().type(),
                clue.acquisition().locationId() == null
                        ? null
                        : safeGeneratedValue(clue.acquisition().locationId(), seed)
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
