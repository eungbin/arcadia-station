package com.arcadia.station.ai.casegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arcadia.station.ai.validation.CaseBlueprintValidator;
import com.arcadia.station.ai.validation.CaseValidationResult;
import com.arcadia.station.ai.validation.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "arcadia.ai.enabled=true",
        "arcadia.ai.offline-mode=false",
        "arcadia.ai.api-key=test-only-key"
})
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
    void retriesTwiceThenUsesValidatedFallback() throws Exception {
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
    }
}
