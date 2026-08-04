package com.arcadia.station.ai.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "arcadia.ai.offline-mode=true")
class SchemaContractTest {

    @Autowired
    private JsonSchemaRepository schemas;

    @Autowired
    private JsonSchemaContractValidator validator;

    @Autowired
    private FallbackCaseProvider fallback;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void caseFixturePassesRuntimeJsonSchemaRevalidation() throws Exception {
        String json = objectMapper.writeValueAsString(
                fallback.forSession("schema-test", "schema-seed")
        );

        assertThat(validator.validate(json, schemas.get("case_blueprint"))).isEmpty();
    }

    @Test
    void structuredOutputObjectSchemasRejectAdditionalProperties() {
        for (String schemaName : List.of(
                "case_blueprint",
                "npc_turn",
                "rag_summary",
                "world_template",
                "mystery_rule_template"
        )) {
            assertObjectsAreClosed(schemas.get(schemaName).schema(), "$");
        }
    }

    @Test
    void generatedCaseSchemaOnlyAllowsRuntimeSupportedAcquisitionTypes() {
        JsonNode acquisitionTypes = schemas.get("case_blueprint")
                .schema()
                .at("/$defs/acquisition/properties/type/enum");

        assertThat(acquisitionTypes)
                .extracting(JsonNode::asText)
                .containsExactly("EXPLORE", "RAG_QUERY", "CONNECT");
    }

    private void assertObjectsAreClosed(JsonNode node, String path) {
        if (node.isObject() && "object".equals(node.path("type").asText())) {
            assertThat(node.has("additionalProperties"))
                    .as(path + " must declare additionalProperties")
                    .isTrue();
            assertThat(node.path("additionalProperties").asBoolean(true))
                    .as(path + " must reject extra properties")
                    .isFalse();
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    assertObjectsAreClosed(entry.getValue(), path + "/" + entry.getKey()));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertObjectsAreClosed(node.get(index), path + "/" + index);
            }
        }
    }
}
