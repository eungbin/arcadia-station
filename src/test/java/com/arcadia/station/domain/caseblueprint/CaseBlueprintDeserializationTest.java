package com.arcadia.station.domain.caseblueprint;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseBlueprintDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void AI_서버_응답_스키마를_CaseBlueprint로_역직렬화한다() throws Exception {
        CaseBlueprint blueprint;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/schema-sample-case-blueprint.json")) {
            blueprint = objectMapper.readValue(in, CaseBlueprint.class);
        }

        assertThat(blueprint.blueprintId()).isEqualTo("case_01ARCADIA");
        assertThat(blueprint.culpritId()).isEqualTo("SOPHIA");
        assertThat(blueprint.worldTemplate()).isEqualTo(new TemplateRef("world-station-01", "1.0.0"));

        assertThat(blueprint.timeline()).hasSize(1);
        assertThat(blueprint.timeline().get(0).actionType()).isEqualTo(TimelineActionType.SYSTEM_ACTION);

        Clue clue = blueprint.clues().get(0);
        assertThat(clue.clueType()).isEqualTo(ClueType.DIGITAL);
        assertThat(clue.solutionRoles()).containsExactly(EvidenceRole.TRIGGER);
        // nullable 필드(acquisition.locationId/characterId)가 null로 정상 역직렬화되는지 확인
        assertThat(clue.acquisition().type()).isEqualTo(AcquisitionType.RAG_QUERY);
        assertThat(clue.acquisition().locationId()).isNull();
        assertThat(clue.acquisition().characterId()).isNull();

        assertThat(blueprint.evidenceRecords().get(0).visibility()).isEqualTo(RecordVisibility.SEARCHABLE);
        assertThat(blueprint.npcKnowledge().get(0).revealPolicies().get(0).responseMode())
                .isEqualTo(ResponseMode.PARTIAL_ADMISSION);

        Solution solution = blueprint.solution();
        assertThat(solution.requiredEvidenceByRole())
                .containsKeys("SETUP", "TRIGGER", "OPPORTUNITY", "MOTIVE", "VICTIM_CONDITION");
        assertThat(solution.requiredEvidenceByRole().get("TRIGGER")).isEqualTo(List.of("CLUE-TRIGGER-LOG"));
        assertThat(solution.nonCulpritExclusions().get(0).characterId()).isEqualTo("MARCUS");
    }
}
