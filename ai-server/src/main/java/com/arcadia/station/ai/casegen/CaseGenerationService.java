package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.AiUsageRecorder;
import com.arcadia.station.ai.common.AiQuotaExceededException;
import com.arcadia.station.ai.template.TemplateRepository;
import com.arcadia.station.ai.validation.CaseBlueprintValidator;
import com.arcadia.station.ai.validation.CaseValidationResult;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CaseGenerationService {

    private final TemplateRepository templates;
    private final CaseBlueprintGenerator generator;
    private final CaseBlueprintValidator validator;
    private final FallbackCaseProvider fallback;
    private final SessionCaseFreezer freezer;
    private final ArcadiaAiProperties properties;
    private final AiUsageRecorder usageRecorder;
    private final CaseGenerationDiagnostics diagnostics;

    public CaseGenerationService(
            TemplateRepository templates,
            CaseBlueprintGenerator generator,
            CaseBlueprintValidator validator,
            FallbackCaseProvider fallback,
            SessionCaseFreezer freezer,
            ArcadiaAiProperties properties,
            AiUsageRecorder usageRecorder,
            CaseGenerationDiagnostics diagnostics
    ) {
        this.templates = templates;
        this.generator = generator;
        this.validator = validator;
        this.fallback = fallback;
        this.freezer = freezer;
        this.properties = properties;
        this.usageRecorder = usageRecorder;
        this.diagnostics = diagnostics;
    }

    public FrozenCaseBlueprint createCase(String sessionId, String seed) {
        Instant createdAt = Instant.now();
        int maximumAttempts = 1 + properties.caseGeneration().maxRetries();
        CaseGenerationFallbackReason configurationReason = configurationFallbackReason();
        boolean externalAiEnabled = configurationReason == CaseGenerationFallbackReason.NONE;
        diagnostics.generationStarted(
                sessionId,
                maximumAttempts,
                externalAiEnabled,
                configurationReason
        );
        if (!externalAiEnabled) {
            return validatedFallback(
                    sessionId,
                    seed,
                    0,
                    configurationReason,
                    false,
                    createdAt
            );
        }

        List<ValidationIssue> previousIssues = List.of();
        CaseGenerationFallbackReason exhaustedReason =
                CaseGenerationFallbackReason.VALIDATION_EXHAUSTED;
        boolean aiPathAttempted = false;
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            diagnostics.attemptStarted(
                    sessionId,
                    attempt,
                    maximumAttempts,
                    previousIssues
            );
            try {
                aiPathAttempted = true;
                CaseBlueprint blueprint = generator.generate(new CaseGenerationRequest(
                        sessionId,
                        seed,
                        templates.world(),
                        templates.rules(),
                        previousIssues
                ));
                CaseValidationResult result = validator.validate(
                        templates.world(),
                        templates.rules(),
                        blueprint
                );
                if (!seed.equals(blueprint.seed())) {
                    previousIssues = List.of(ValidationIssue.of(
                            "SEED_MISMATCH",
                            "$.seed",
                            "Generated seed does not match the server seed"
                    ));
                    usageRecorder.recordValidationIssue("SEED_MISMATCH");
                    diagnostics.attemptRejected(sessionId, attempt, previousIssues);
                    exhaustedReason = CaseGenerationFallbackReason.VALIDATION_EXHAUSTED;
                    continue;
                }
                if (result.valid()) {
                    FrozenCaseBlueprint frozen = freezer.freeze(
                            sessionId,
                            blueprint,
                            attempt,
                            GenerationSource.AI,
                            properties.activeModel(),
                            properties.caseGeneration().promptVersion(),
                            createdAt
                    );
                    usageRecorder.recordGenerationSource(GenerationSource.AI.name());
                    diagnostics.generationCompleted(
                            frozen,
                            CaseGenerationFallbackReason.NONE,
                            true,
                            createdAt
                    );
                    return frozen;
                }
                previousIssues = result.issues();
                previousIssues.forEach(issue -> usageRecorder.recordValidationIssue(issue.code()));
                diagnostics.attemptRejected(sessionId, attempt, previousIssues);
                exhaustedReason = CaseGenerationFallbackReason.VALIDATION_EXHAUSTED;
            } catch (AiQuotaExceededException exception) {
                diagnostics.attemptFailed(
                        sessionId,
                        attempt,
                        CaseGenerationFallbackReason.QUOTA_EXCEEDED,
                        exception
                );
                return validatedFallback(
                        sessionId,
                        seed,
                        attempt,
                        CaseGenerationFallbackReason.QUOTA_EXCEEDED,
                        true,
                        createdAt
                );
            } catch (RuntimeException exception) {
                previousIssues = List.of(ValidationIssue.of(
                        "AI_GENERATION_FAILURE",
                        "$",
                        exception.getClass().getSimpleName()
                ));
                usageRecorder.recordValidationIssue("AI_GENERATION_FAILURE");
                diagnostics.attemptFailed(
                        sessionId,
                        attempt,
                        CaseGenerationFallbackReason.GENERATION_FAILURE,
                        exception
                );
                exhaustedReason = CaseGenerationFallbackReason.GENERATION_FAILURE;
            }
        }
        return validatedFallback(
                sessionId,
                seed,
                maximumAttempts,
                exhaustedReason,
                aiPathAttempted,
                createdAt
        );
    }

    private FrozenCaseBlueprint validatedFallback(
            String sessionId,
            String seed,
            int attempts,
            CaseGenerationFallbackReason fallbackReason,
            boolean aiPathAttempted,
            Instant createdAt
    ) {
        CaseBlueprint blueprint = fallback.forSession(sessionId, seed);
        CaseValidationResult result = validator.validate(
                templates.world(),
                templates.rules(),
                blueprint
        );
        if (!result.valid()) {
            throw new IllegalStateException("Built-in fallback failed validation: " + result.issues());
        }
        FrozenCaseBlueprint frozen = freezer.freeze(
                sessionId,
                blueprint,
                attempts,
                GenerationSource.FALLBACK,
                properties.activeModel(),
                properties.caseGeneration().promptVersion(),
                createdAt
        );
        usageRecorder.recordGenerationSource(GenerationSource.FALLBACK.name());
        diagnostics.generationCompleted(
                frozen,
                fallbackReason,
                aiPathAttempted,
                createdAt
        );
        return frozen;
    }

    private CaseGenerationFallbackReason configurationFallbackReason() {
        if (!properties.enabled()) {
            return CaseGenerationFallbackReason.AI_DISABLED;
        }
        if (properties.offlineMode()) {
            return CaseGenerationFallbackReason.OFFLINE_MODE;
        }
        if (!properties.hasActiveApiKey()) {
            return CaseGenerationFallbackReason.MISSING_API_KEY;
        }
        return CaseGenerationFallbackReason.NONE;
    }
}
