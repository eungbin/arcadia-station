package com.arcadia.station.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import com.arcadia.station.ai.template.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "arcadia.ai.offline-mode=true")
class CaseBlueprintValidatorTest {

    @Autowired
    private TemplateRepository templates;

    @Autowired
    private FallbackCaseProvider fallback;

    @Autowired
    private CaseBlueprintValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    private ObjectNode validCase;

    @BeforeEach
    void setUp() {
        validCase = objectMapper.valueToTree(
                fallback.forSession("validator-test", "validator-seed")
        );
    }

    @Test
    void rejectsUnknownWorldId() throws Exception {
        validCase.withObject("/method/setupAction").put("locationId", "UNKNOWN_ROOM");

        assertThat(codes(validate(validCase))).contains("UNKNOWN_LOCATION_ID");
    }

    @Test
    void rejectsCapabilityMismatch() throws Exception {
        validCase.withObject("/method/triggerAction")
                .put("operation", "SECURITY_MASTER_OVERRIDE");

        assertThat(codes(validate(validCase))).contains("CAPABILITY_MISMATCH");
    }

    @Test
    void rejectsClueGraphCycle() throws Exception {
        ArrayNode clues = (ArrayNode) validCase.get("clues");
        clues.get(0).withObject("/acquisition")
                .withArray("requiredClueIds")
                .add(clues.get(1).get("clueId").asText());
        clues.get(1).withObject("/acquisition")
                .withArray("requiredClueIds")
                .add(clues.get(0).get("clueId").asText());

        assertThat(codes(validate(validCase))).contains("CLUE_GRAPH_CYCLE");
    }

    @Test
    void rejectsNonUniqueCulprit() throws Exception {
        ArrayNode effects = (ArrayNode) validCase.path("clues").get(0).path("suspectEffects");
        for (int index = effects.size() - 1; index >= 0; index--) {
            if ("YUNA".equals(effects.get(index).path("characterId").asText())) {
                effects.remove(index);
            }
        }

        assertThat(codes(validate(validCase))).contains("CULPRIT_NOT_UNIQUE");
    }

    @Test
    void rejectsMissingRequiredRole() throws Exception {
        validCase.withObject("/solution/requiredEvidenceByRole")
                .set("MOTIVE", objectMapper.createArrayNode());

        assertThat(codes(validate(validCase))).contains("MISSING_REQUIRED_ROLE");
    }

    private CaseValidationResult validate(ObjectNode node) throws Exception {
        return validator.validate(
                templates.world(),
                templates.rules(),
                objectMapper.treeToValue(node, CaseBlueprint.class)
        );
    }

    private Set<String> codes(CaseValidationResult result) {
        return result.issues().stream()
                .map(ValidationIssue::code)
                .collect(Collectors.toSet());
    }
}
