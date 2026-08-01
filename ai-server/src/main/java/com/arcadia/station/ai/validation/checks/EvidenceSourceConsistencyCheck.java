package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(35)
public class EvidenceSourceConsistencyCheck implements CaseBlueprintCheck {

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, WorldTemplate.EvidenceSourceDefinition> definitions =
                world.evidenceSources().stream()
                        .collect(Collectors.toMap(
                                WorldTemplate.EvidenceSourceDefinition::type,
                                Function.identity()
                        ));
        Map<String, CaseBlueprint.EvidenceRecord> records = blueprint.evidenceRecords().stream()
                .collect(Collectors.toMap(
                        CaseBlueprint.EvidenceRecord::recordId,
                        Function.identity()
                ));
        for (CaseBlueprint.EvidenceRecord record : blueprint.evidenceRecords()) {
            WorldTemplate.EvidenceSourceDefinition definition = definitions.get(record.recordType());
            if (definition == null) {
                continue;
            }
            for (String requiredKey : definition.requiredMetadataKeys()) {
                String value = record.metadata().get(requiredKey);
                if (value == null || value.isBlank()) {
                    issues.add(ValidationIssue.of(
                            "EVIDENCE_METADATA_MISSING",
                            "$.evidenceRecords[" + record.recordId() + "].metadata." + requiredKey,
                            requiredKey
                    ));
                }
            }
        }
        for (CaseBlueprint.Clue clue : blueprint.clues()) {
            if (clue.acquisition().type() != CaseBlueprint.AcquisitionType.RAG_QUERY) {
                continue;
            }
            CaseBlueprint.EvidenceRecord record = records.get(clue.source().sourceId());
            if (record != null && record.visibility() != CaseBlueprint.RecordVisibility.SEARCHABLE) {
                issues.add(ValidationIssue.of(
                        "MANDATORY_RAG_RECORD_HIDDEN",
                        "$.clues[" + clue.clueId() + "].source",
                        record.recordId()
                ));
            }
            if (record != null && !record.revealsClueIds().contains(clue.clueId())) {
                issues.add(ValidationIssue.of(
                        "EVIDENCE_CLUE_LINK_MISMATCH",
                        "$.clues[" + clue.clueId() + "].source",
                        record.recordId()
                ));
            }
        }
        return List.copyOf(issues);
    }
}
