package com.arcadia.station.ai.casegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arcadia.station.ai.common.AiQuotaExceededException;
import com.arcadia.station.ai.validation.CaseBlueprintValidator;
import com.arcadia.station.ai.validation.CaseValidationResult;
import com.arcadia.station.ai.validation.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(properties = {
        "arcadia.ai.enabled=true",
        "arcadia.ai.offline-mode=false",
        "arcadia.ai.provider=openai",
        "arcadia.ai.openai.api-key=test-only-key"
})
@ExtendWith(OutputCaptureExtension.class)
class CaseGenerationServiceRetryTest {

    @MockBean
    private CaseBlueprintGenerator generator;

    @MockBean
    private CaseBlueprintValidator validator;

    @Autowired
    private CaseGenerationService service;

    @Autowired
    private FallbackCaseProvider fallback;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void successfulAiGenerationLogsApiModeAndEveryClue(CapturedOutput output) {
        CaseBlueprint generated = fallback.forSession(
                "ai-success-test",
                "ai-success-seed"
        );
        when(generator.generate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(generated);
        when(validator.validate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(generated)
        )).thenReturn(new CaseValidationResult(List.of()));

        FrozenCaseBlueprint result = service.createCase(
                "ai-success-test",
                "ai-success-seed"
        );

        assertThat(result.generationSource()).isEqualTo(GenerationSource.AI);
        assertThat(result.generationAttemptCount()).isEqualTo(1);
        verify(generator, times(1)).generate(org.mockito.ArgumentMatchers.any());
        String logs = output.getOut();
        assertThat(logs)
                .contains("[AI-CASE][START] event=ai_case_start "
                        + "sessionId=ai-success-test configuredMode=API "
                        + "fallbackReason=NONE")
                .contains("[AI-CASE][RESULT] event=ai_case_result "
                        + "sessionId=ai-success-test mode=API generationSource=AI "
                        + "fallbackReason=NONE aiPathAttempted=true")
                .contains("[AI-CASE][END] event=ai_case_clue_list_complete "
                        + "sessionId=ai-success-test mode=API loggedClueCount=14 "
                        + "totalClueCount=14 clueManifestTruncated=false");
        assertThat(logs.lines()
                .filter(line -> line.contains("[AI-CASE][CLUE]"))
                .filter(line -> line.contains("sessionId=ai-success-test")))
                .hasSize(14);
    }

    @Test
    void retriesTwiceThenUsesValidatedFallback(CapturedOutput output) throws Exception {
        ObjectNode invalidNode = objectMapper.valueToTree(
                fallback.forSession("retry-test", "retry-seed")
        );
        invalidNode.withObject("/method/setupAction").put("locationId", "UNKNOWN_ROOM");
        CaseBlueprint invalid = objectMapper.treeToValue(invalidNode, CaseBlueprint.class);
        when(generator.generate(org.mockito.ArgumentMatchers.any())).thenReturn(invalid);
        when(validator.validate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(invalid)
        )).thenReturn(new CaseValidationResult(List.of(
                ValidationIssue.of("UNKNOWN_WORLD_ID", "$.method.setupAction.locationId", "UNKNOWN_ROOM")
        )));
        when(validator.validate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(
                        blueprint -> blueprint != invalid
                                && "retry-seed".equals(blueprint.seed())
                )
        )).thenReturn(new CaseValidationResult(List.of()));

        FrozenCaseBlueprint result = service.createCase("retry-test", "retry-seed");

        assertThat(result.generationSource()).isEqualTo(GenerationSource.FALLBACK);
        assertThat(result.generationAttemptCount()).isEqualTo(3);
        verify(generator, times(3)).generate(org.mockito.ArgumentMatchers.any());
        assertThat(output.getOut())
                .contains("reason=VALIDATION_FAILED")
                .contains("validationCodes=[\"UNKNOWN_WORLD_ID\"]")
                .contains("generationSource=FALLBACK fallbackReason=VALIDATION_EXHAUSTED")
                .doesNotContain("UNKNOWN_ROOM");
    }

    @Test
    void quotaFailureUsesFallbackWithoutRetryingProvider(CapturedOutput output) {
        when(generator.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AiQuotaExceededException(
                        "quota exhausted DO_NOT_LOG_PROVIDER_PAYLOAD"
                ));
        when(validator.validate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(
                        blueprint -> "quota-seed".equals(blueprint.seed())
                )
        )).thenReturn(new CaseValidationResult(List.of()));

        FrozenCaseBlueprint result = service.createCase("quota-test", "quota-seed");

        assertThat(result.generationSource()).isEqualTo(GenerationSource.FALLBACK);
        assertThat(result.generationAttemptCount()).isEqualTo(1);
        verify(generator, times(1)).generate(org.mockito.ArgumentMatchers.any());
        assertThat(output.getOut())
                .contains("reason=QUOTA_EXCEEDED exceptionType=AiQuotaExceededException")
                .contains("generationSource=FALLBACK fallbackReason=QUOTA_EXCEEDED")
                .doesNotContain("DO_NOT_LOG_PROVIDER_PAYLOAD");
    }

    @Test
    void providerFailureIsRetriedAndLoggedWithoutProviderPayload(CapturedOutput output) {
        when(generator.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException(
                        "DO_NOT_LOG_RAW_PROVIDER_RESPONSE"
                ));
        when(validator.validate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(
                        blueprint -> "provider-seed".equals(blueprint.seed())
                )
        )).thenReturn(new CaseValidationResult(List.of()));

        FrozenCaseBlueprint result = service.createCase(
                "provider-test",
                "provider-seed"
        );

        assertThat(result.generationSource()).isEqualTo(GenerationSource.FALLBACK);
        assertThat(result.generationAttemptCount()).isEqualTo(3);
        verify(generator, times(3)).generate(org.mockito.ArgumentMatchers.any());
        assertThat(output.getOut())
                .contains("reason=GENERATION_FAILURE exceptionType=IllegalStateException")
                .contains("generationSource=FALLBACK fallbackReason=GENERATION_FAILURE")
                .doesNotContain("DO_NOT_LOG_RAW_PROVIDER_RESPONSE");
    }
}
