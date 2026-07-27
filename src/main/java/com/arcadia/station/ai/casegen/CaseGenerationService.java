package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.AiUsageRecorder;
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

    public CaseGenerationService(
            TemplateRepository templates,
            CaseBlueprintGenerator generator,
            CaseBlueprintValidator validator,
            FallbackCaseProvider fallback,
            SessionCaseFreezer freezer,
            ArcadiaAiProperties properties,
            AiUsageRecorder usageRecorder
    ) {
        this.templates = templates;
        this.generator = generator;
        this.validator = validator;
        this.fallback = fallback;
        this.freezer = freezer;
        this.properties = properties;
        this.usageRecorder = usageRecorder;
    }

    public FrozenCaseBlueprint createCase(String sessionId, String seed) {
        Instant createdAt = Instant.now();
        if (!properties.enabled() || properties.offlineMode()
                || properties.apiKey() == null || properties.apiKey().isBlank()) {
            return validatedFallback(sessionId, seed, 0, createdAt);
        }

        List<ValidationIssue> previousIssues = List.of();
        int maximumAttempts = 1 + properties.caseGeneration().maxRetries();
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
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
                    continue;
                }
                if (result.valid()) {
                    FrozenCaseBlueprint frozen = freezer.freeze(
                            sessionId,
                            blueprint,
                            attempt,
                            GenerationSource.AI,
                            properties.model(),
                            properties.caseGeneration().promptVersion(),
                            createdAt
                    );
                    usageRecorder.recordGenerationSource(GenerationSource.AI.name());
                    return frozen;
                }
                previousIssues = result.issues();
                previousIssues.forEach(issue -> usageRecorder.recordValidationIssue(issue.code()));
            } catch (RuntimeException exception) {
                previousIssues = List.of(ValidationIssue.of(
                        "AI_GENERATION_FAILURE",
                        "$",
                        exception.getClass().getSimpleName()
                ));
            }
        }
        return validatedFallback(sessionId, seed, maximumAttempts, createdAt);
    }

    private FrozenCaseBlueprint validatedFallback(
            String sessionId,
            String seed,
            int attempts,
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
                properties.model(),
                properties.caseGeneration().promptVersion(),
                createdAt
        );
        usageRecorder.recordGenerationSource(GenerationSource.FALLBACK.name());
        return frozen;
    }
}
