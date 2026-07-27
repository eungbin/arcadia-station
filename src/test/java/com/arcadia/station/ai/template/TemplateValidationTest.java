package com.arcadia.station.ai.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.arcadia.station.ai.common.OpenAiGateway;
import com.arcadia.station.ai.common.StructuredPrompt;
import com.arcadia.station.ai.validation.CaseBlueprintValidator;
import com.arcadia.station.ai.validation.MysteryRuleTemplateValidator;
import com.arcadia.station.ai.validation.WorldTemplateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "arcadia.ai.offline-mode=true")
class TemplateValidationTest {

    @Autowired
    private TemplateRepository templates;

    @Autowired
    private WorldTemplateValidator worldValidator;

    @Autowired
    private MysteryRuleTemplateValidator ruleValidator;

    @Autowired
    private FallbackCaseProvider fallback;

    @Autowired
    private CaseBlueprintValidator caseValidator;

    @Autowired
    private OpenAiGateway openAiGateway;

    @Autowired
    private JsonSchemaRepository schemas;

    @Test
    void fixedTemplatesAndFallbackPassAllValidators() {
        assertThat(worldValidator.validate(templates.world())).isEmpty();
        assertThat(ruleValidator.validate(templates.world(), templates.rules())).isEmpty();
        assertThat(caseValidator.validate(
                templates.world(),
                templates.rules(),
                fallback.forSession("test-session", "test-seed")
        ).issues()).isEmpty();
    }

    @Test
    void fakeGatewayProducesACompleteValidCaseWithoutAnApiKey() {
        CaseBlueprint generated = openAiGateway.generateStructured(
                AiPurpose.CASE_GENERATION,
                "case-generator-v1",
                new StructuredPrompt(
                        "test",
                        "{\"sessionId\":\"fake-session\",\"seed\":\"fake-seed\"}"
                ),
                schemas.get("case_blueprint"),
                CaseBlueprint.class
        );

        assertThat(generated.seed()).isEqualTo("fake-seed");
        assertThat(caseValidator.validate(
                templates.world(),
                templates.rules(),
                generated
        ).issues()).isEmpty();
    }
}
