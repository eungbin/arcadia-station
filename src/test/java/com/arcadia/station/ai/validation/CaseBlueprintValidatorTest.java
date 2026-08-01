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

        assertThat(codes(validate(validCase)))
                .contains("UNKNOWN_LOCATION_ID", "NON_ROSTER_LOCATION_ID");
    }

    @Test
    void rejectsNonRosterLocationInEveryGeneratedLocationField() throws Exception {
        validCase.withObject("/method/setupAction").put("locationId", "LEGACY_SETUP_ROOM");
        ((ObjectNode) validCase.withArray("timeline").get(0))
                .put("locationId", "LEGACY_TIMELINE_ROOM");
        validCase.withArray("clues").get(0)
                .withObject("/acquisition")
                .put("locationId", "LEGACY_CLUE_ROOM");
        validCase.withArray("evidenceRecords").get(0)
                .withObject("/metadata")
                .put("locationId", "LEGACY_RECORD_ROOM");

        assertThat(validate(validCase).issues())
                .filteredOn(issue -> issue.code().equals("NON_ROSTER_LOCATION_ID"))
                .extracting(ValidationIssue::path)
                .contains(
                        "$.method.setupAction.locationId",
                        "$.timeline[EVT-001].locationId",
                        "$.clues[CLUE-SETUP-PANEL].acquisition.locationId",
                        "$.evidenceRecords[RECORD-TRIGGER].metadata.locationId"
                );
    }

    @Test
    void rejectsMissingRequiredFrontendObjectClue() throws Exception {
        removeClueBySourceId("CO_BODY");

        assertThat(codes(validate(validCase)))
                .contains("MISSING_REQUIRED_OBJECT_CLUE");
    }

    @Test
    void rejectsExploreClueWithWrongObjectLocation() throws Exception {
        clueBySourceId("EN_LIFE_SUPPORT")
                .withObject("/acquisition")
                .put("locationId", "CENTRAL_HUB");

        assertThat(codes(validate(validCase)))
                .contains("EXPLORE_OBJECT_LOCATION_MISMATCH");
    }

    @Test
    void rejectsExploreClueWithNonPhysicalObjectSource() throws Exception {
        clueBySourceId("CO_BODY")
                .withObject("/source")
                .put("sourceType", "COMMAND_LOG");

        assertThat(codes(validate(validCase)))
                .contains("EXPLORE_OBJECT_SOURCE_TYPE_MISMATCH");
    }

    @Test
    void rejectsUnknownExploreObjectId() throws Exception {
        clueBySourceId("CO_BODY")
                .withObject("/source")
                .put("sourceId", "UNKNOWN_FRONTEND_OBJECT");

        assertThat(codes(validate(validCase)))
                .contains(
                        "UNKNOWN_EXPLORATION_OBJECT_ID",
                        "MISSING_REQUIRED_OBJECT_CLUE"
                );
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

    @Test
    void rejectsMissingNpcKnowledgeForAlibiCharacter() throws Exception {
        removeByCharacterId((ArrayNode) validCase.path("npcKnowledge"), "MAYA");

        assertThat(codes(validate(validCase))).contains("MISSING_NPC_KNOWLEDGE");
    }

    @Test
    void rejectsDuplicateNpcKnowledgeCharacter() throws Exception {
        ArrayNode knowledge = (ArrayNode) validCase.path("npcKnowledge");
        knowledge.add(knowledge.get(0).deepCopy());

        assertThat(codes(validate(validCase)))
                .contains("DUPLICATE_NPC_KNOWLEDGE_CHARACTER_ID");
    }

    @Test
    void rejectsMissingInterrogationFallbackFields() throws Exception {
        knowledge("MAYA").withArray("initialClaimFactIds").removeAll();
        knowledge("JUNHO").withArray("recommendedQuestionTopics").removeAll();

        assertThat(codes(validate(validCase)))
                .contains(
                        "MISSING_INITIAL_CLAIM_FACT",
                        "MISSING_RECOMMENDED_QUESTION_TOPIC"
                );
    }

    @Test
    void rejectsInitialClaimFactNotLinkedToAlibi() throws Exception {
        knowledge("MAYA").withArray("initialClaimFactIds")
                .set(0, objectMapper.getNodeFactory().textNode("FACT-SOPHIA-CLAIM"));

        assertThat(codes(validate(validCase)))
                .contains("INITIAL_CLAIM_FACT_NOT_LINKED_TO_ALIBI");
    }

    @Test
    void rejectsCharacterIdCaseMismatch() throws Exception {
        knowledge("MAYA").put("characterId", "Maya");

        assertThat(codes(validate(validCase)))
                .contains("UNKNOWN_CHARACTER_ID", "MISSING_NPC_KNOWLEDGE");
    }

    @Test
    void rejectsIncompleteNonCulpritExclusions() throws Exception {
        removeByCharacterId(
                (ArrayNode) validCase.withObject("/solution").path("nonCulpritExclusions"),
                "YUNA"
        );

        assertThat(codes(validate(validCase)))
                .contains("MISSING_NON_CULPRIT_EXCLUSION");
    }

    @Test
    void rejectsSuspectEffectForCharacterOutsideAlibis() throws Exception {
        ObjectNode effect = (ObjectNode) validCase.withArray("clues")
                .get(0)
                .path("suspectEffects")
                .get(0);
        effect.put("characterId", "VICTIM");

        assertThat(codes(validate(validCase)))
                .contains("NON_ALIBI_SUSPECT_EFFECT");
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

    private ObjectNode knowledge(String characterId) {
        for (var node : validCase.withArray("npcKnowledge")) {
            if (characterId.equals(node.path("characterId").asText())) {
                return (ObjectNode) node;
            }
        }
        throw new IllegalArgumentException("Missing npcKnowledge: " + characterId);
    }

    private ObjectNode clueBySourceId(String sourceId) {
        for (var node : validCase.withArray("clues")) {
            if (sourceId.equals(node.path("source").path("sourceId").asText())) {
                return (ObjectNode) node;
            }
        }
        throw new IllegalArgumentException("Missing clue source: " + sourceId);
    }

    private void removeClueBySourceId(String sourceId) {
        ArrayNode clues = validCase.withArray("clues");
        for (int index = clues.size() - 1; index >= 0; index--) {
            if (sourceId.equals(clues.get(index).path("source").path("sourceId").asText())) {
                clues.remove(index);
            }
        }
    }

    private void removeByCharacterId(ArrayNode values, String characterId) {
        for (int index = values.size() - 1; index >= 0; index--) {
            if (characterId.equals(values.get(index).path("characterId").asText())) {
                values.remove(index);
            }
        }
    }
}
