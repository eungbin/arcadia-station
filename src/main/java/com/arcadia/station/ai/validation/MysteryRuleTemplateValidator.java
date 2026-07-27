package com.arcadia.station.ai.validation;

import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MysteryRuleTemplateValidator {

    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> characterIds = world.characters().stream()
                .map(WorldTemplate.CharacterDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        if (rules.culpritPolicy().type() == MysteryRuleTemplate.CulpritPolicyType.FIXED
                && !characterIds.contains(rules.culpritPolicy().culpritId())) {
            issues.add(ValidationIssue.of(
                    "UNKNOWN_CULPRIT",
                    "$.culpritPolicy.culpritId",
                    rules.culpritPolicy().culpritId()
            ));
        }
        if (new HashSet<>(rules.requiredEvidenceRoles()).size()
                != rules.requiredEvidenceRoles().size()) {
            issues.add(ValidationIssue.of(
                    "DUPLICATE_REQUIRED_ROLE",
                    "$.requiredEvidenceRoles",
                    "Required roles must be unique"
            ));
        }
        MysteryRuleTemplate.IntRange core = rules.clueRules().coreClueCount();
        MysteryRuleTemplate.IntRange red = rules.clueRules().redHerringCount();
        if (core.min() < 1 || core.max() < core.min()) {
            issues.add(ValidationIssue.of(
                    "INVALID_CLUE_RANGE",
                    "$.clueRules.coreClueCount",
                    core.toString()
            ));
        }
        if (red.min() < 0 || red.max() < red.min()) {
            issues.add(ValidationIssue.of(
                    "INVALID_CLUE_RANGE",
                    "$.clueRules.redHerringCount",
                    red.toString()
            ));
        }
        if (core.max() < rules.requiredEvidenceRoles().size()) {
            issues.add(ValidationIssue.of(
                    "INSUFFICIENT_CORE_CLUES",
                    "$.clueRules.coreClueCount.max",
                    "Core clue maximum is smaller than required role count"
            ));
        }
        if (!rules.solutionRules().requireUniqueCulprit()) {
            issues.add(ValidationIssue.of(
                    "UNIQUE_CULPRIT_DISABLED",
                    "$.solutionRules.requireUniqueCulprit",
                    "Unique culprit validation must be enabled"
            ));
        }
        Set<EvidenceRole> generationRoles = new HashSet<>(rules.requiredEvidenceRoles());
        Set<EvidenceRole> reportRoles = new HashSet<>(rules.finalReportRules().requiredRoles());
        if (!generationRoles.equals(reportRoles)) {
            issues.add(ValidationIssue.of(
                    "FINAL_REPORT_ROLE_MISMATCH",
                    "$.finalReportRules.requiredRoles",
                    "Final report roles must equal generation roles"
            ));
        }
        return List.copyOf(issues);
    }
}
