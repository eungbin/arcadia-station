package com.arcadia.station.domain.caseblueprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 서버팀이 실제로 전달한 원본 case-blueprint.schema.json(SHA-256 0d454426...deca 확인됨)과
 * 실제 READY 응답 예시(internal-case-ready.response.json)를 사용해 두 가지를 확인한다.
 *
 * 1) AI 서버가 실제로 보내는 caseBlueprint가 원본 JSON Schema를 통과하는지(스키마 검증기 사용)
 * 2) 그 동일한 JSON을 우리 CaseBlueprint(및 하위 25개 record/enum)로 문제없이 역직렬화할 수 있는지
 */
class CaseBlueprintRealSchemaValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void AI_서버의_실제_caseBlueprint_예시가_원본_스키마를_통과한다() throws Exception {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        Schema schema;
        try (InputStream schemaIn = getClass().getResourceAsStream("/ai-server/case-blueprint.schema.json")) {
            schema = registry.getSchema(schemaIn);
        }

        JsonNode caseBlueprintNode;
        try (InputStream responseIn = getClass().getResourceAsStream("/ai-server/internal-case-ready.response.json")) {
            JsonNode fullResponse = objectMapper.readTree(responseIn);
            caseBlueprintNode = fullResponse.path("generation").path("caseBlueprint");
        }

        List<Error> errors = schema.validate(caseBlueprintNode);

        assertThat(errors).as("스키마 위반: %s", errors).isEmpty();
    }

    @Test
    void 동일한_caseBlueprint_JSON을_우리_CaseBlueprint로_역직렬화할_수_있다() throws Exception {
        String caseBlueprintJson;
        try (InputStream responseIn = getClass().getResourceAsStream("/ai-server/internal-case-ready.response.json")) {
            JsonNode fullResponse = objectMapper.readTree(responseIn);
            caseBlueprintJson = fullResponse.path("generation").path("caseBlueprint").toString();
        }

        CaseBlueprint blueprint = objectMapper.readValue(caseBlueprintJson, CaseBlueprint.class);

        assertThat(blueprint.blueprintId()).isEqualTo("CASE-BACKENDCONTRACT001");
        assertThat(blueprint.culpritId()).isEqualTo("SOPHIA");
        assertThat(blueprint.clues()).hasSize(5);
        assertThat(blueprint.solution().requiredEvidenceByRole())
                .containsKeys("SETUP", "TRIGGER", "OPPORTUNITY", "MOTIVE", "VICTIM_CONDITION");
    }
}
