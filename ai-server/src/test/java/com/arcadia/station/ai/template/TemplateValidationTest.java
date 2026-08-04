package com.arcadia.station.ai.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.CaseGenerationRequest;
import com.arcadia.station.ai.casegen.CasePromptAssembler;
import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.arcadia.station.ai.common.OpenAiGateway;
import com.arcadia.station.ai.common.StructuredPrompt;
import com.arcadia.station.ai.validation.CaseBlueprintValidator;
import com.arcadia.station.ai.validation.MysteryRuleTemplateValidator;
import com.arcadia.station.ai.validation.WorldTemplateValidator;
import java.util.List;
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

    @Autowired
    private CasePromptAssembler promptAssembler;

    @Test
    void fixedTemplatesAndFallbackPassAllValidators() {
        assertThat(templates.world().version()).isEqualTo("1.1.0");
        assertThat(templates.rules().version()).isEqualTo("1.1.0");
        assertThat(templates.world().locations())
                .extracting(WorldTemplate.LocationDefinition::id)
                .containsExactlyElementsOf(ArcadiaLocationRoster.IDS);
        assertThat(worldValidator.validate(templates.world())).isEmpty();
        assertThat(ruleValidator.validate(templates.world(), templates.rules())).isEmpty();
        CaseBlueprint fallbackCase = fallback.forSession("test-session", "test-seed");
        assertThat(fallbackCase.clues()).hasSize(14);
        assertThat(fallbackCase.clues().stream()
                .filter(clue -> clue.acquisition().type()
                        == CaseBlueprint.AcquisitionType.EXPLORE)
                .toList()).hasSize(10);
        assertThat(caseValidator.validate(
                templates.world(),
                templates.rules(),
                fallbackCase
        ).issues()).isEmpty();
    }

    @Test
    void fakeGatewayProducesACompleteValidCaseWithoutAnApiKey() {
        CaseBlueprint generated = openAiGateway.generateStructured(
                AiPurpose.CASE_GENERATION,
                "case-generator-v2",
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

    @Test
    void casePromptOnlyRequestsRuntimeSupportedAcquisitionTypes() {
        StructuredPrompt prompt = promptAssembler.assemble(new CaseGenerationRequest(
                "prompt-test",
                "prompt-seed",
                templates.world(),
                templates.rules(),
                List.of()
        ));

        assertThat(prompt.system())
                .contains("EXPLORE, RAG_QUERY 또는 CONNECT")
                .doesNotContain("INTERROGATE")
                .doesNotContain("AUTO");
    }
}
