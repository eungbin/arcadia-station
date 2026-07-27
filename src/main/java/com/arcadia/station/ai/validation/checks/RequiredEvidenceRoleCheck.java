package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
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
@Order(50)
public class RequiredEvidenceRoleCheck implements CaseBlueprintCheck {

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, CaseBlueprint.Clue> clues = blueprint.clues().stream()
                .collect(Collectors.toMap(CaseBlueprint.Clue::clueId, Function.identity()));
        for (EvidenceRole role : rules.requiredEvidenceRoles()) {
            List<String> ids = blueprint.solution().requiredEvidenceByRole()
                    .getOrDefault(role, List.of());
            if (ids.isEmpty()) {
                issues.add(ValidationIssue.of(
                        "MISSING_REQUIRED_ROLE",
                        "$.solution.requiredEvidenceByRole." + role,
                        role.name()
                ));
                continue;
            }
            for (String id : ids) {
                CaseBlueprint.Clue clue = clues.get(id);
                if (clue != null && !clue.solutionRoles().contains(role)) {
                    issues.add(ValidationIssue.of(
                            "ROLE_CLUE_MISMATCH",
                            "$.solution.requiredEvidenceByRole." + role,
                            id
                    ));
                }
                if (clue != null && !clue.isCore()) {
                    issues.add(ValidationIssue.of(
                            "SOLUTION_CLUE_NOT_CORE",
                            "$.solution.requiredEvidenceByRole." + role,
                            id
                    ));
                }
                if (clue != null && clue.suspectEffects().stream().anyMatch(
                        effect -> effect.characterId().equals(blueprint.culpritId())
                                && effect.effect() == CaseBlueprint.SuspectEffectType.EXCLUDES)) {
                    issues.add(ValidationIssue.of(
                            "CULPRIT_EXCLUDED_BY_CORE_CLUE",
                            "$.clues[" + id + "].suspectEffects",
                            id
                    ));
                }
            }
        }
        long coreCount = blueprint.clues().stream().filter(CaseBlueprint.Clue::isCore).count();
        MysteryRuleTemplate.IntRange coreRange = rules.clueRules().coreClueCount();
        if (coreCount < coreRange.min() || coreCount > coreRange.max()) {
            issues.add(ValidationIssue.of(
                    "CORE_CLUE_COUNT_OUT_OF_RANGE",
                    "$.clues",
                    String.valueOf(coreCount)
            ));
        }
        long redHerringCount = blueprint.redHerrings().size();
        MysteryRuleTemplate.IntRange redRange = rules.clueRules().redHerringCount();
        if (redHerringCount < redRange.min() || redHerringCount > redRange.max()) {
            issues.add(ValidationIssue.of(
                    "RED_HERRING_COUNT_OUT_OF_RANGE",
                    "$.redHerrings",
                    String.valueOf(redHerringCount)
            ));
        }
        for (Map.Entry<CaseBlueprint.ClueType, Integer> minimum
                : rules.clueRules().minimumByType().entrySet()) {
            long count = blueprint.clues().stream()
                    .filter(CaseBlueprint.Clue::isCore)
                    .filter(clue -> clue.clueType() == minimum.getKey())
                    .count();
            if (count < minimum.getValue()) {
                issues.add(ValidationIssue.of(
                        "MINIMUM_CLUE_TYPE_NOT_MET",
                        "$.clues",
                        minimum.getKey() + ":" + count
                ));
            }
        }
        return List.copyOf(issues);
    }
}
