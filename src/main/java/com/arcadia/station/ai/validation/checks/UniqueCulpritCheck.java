package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)
public class UniqueCulpritCheck implements CaseBlueprintCheck {

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, CaseBlueprint.Clue> clues = blueprint.clues().stream()
                .collect(Collectors.toMap(CaseBlueprint.Clue::clueId, Function.identity()));
        Set<String> requiredClueIds = blueprint.solution().requiredEvidenceByRole().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Set<String> candidates = world.characters().stream()
                .filter(WorldTemplate.CharacterDefinition::suspect)
                .map(WorldTemplate.CharacterDefinition::id)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, Set<String>> excludedBy = new HashMap<>();

        for (String clueId : requiredClueIds) {
            CaseBlueprint.Clue clue = clues.get(clueId);
            if (clue == null) {
                continue;
            }
            for (CaseBlueprint.SuspectEffect effect : clue.suspectEffects()) {
                if (effect.effect() == CaseBlueprint.SuspectEffectType.EXCLUDES) {
                    candidates.remove(effect.characterId());
                    excludedBy.computeIfAbsent(effect.characterId(), ignored -> new HashSet<>())
                            .add(clueId);
                }
            }
        }
        if (!candidates.equals(Set.of(blueprint.culpritId()))) {
            issues.add(ValidationIssue.of(
                    "CULPRIT_NOT_UNIQUE",
                    "$.solution",
                    candidates.toString()
            ));
        }

        Set<String> nonCulprits = world.characters().stream()
                .filter(WorldTemplate.CharacterDefinition::suspect)
                .map(WorldTemplate.CharacterDefinition::id)
                .filter(id -> !id.equals(blueprint.culpritId()))
                .collect(Collectors.toSet());
        nonCulprits.stream()
                .filter(id -> excludedBy.getOrDefault(id, Set.of()).isEmpty())
                .forEach(id -> issues.add(ValidationIssue.of(
                        "NON_CULPRIT_NOT_EXCLUDED",
                        "$.solution.nonCulpritExclusions",
                        id
                )));

        Map<String, CaseBlueprint.NonCulpritExclusion> declared =
                blueprint.solution().nonCulpritExclusions().stream()
                        .collect(Collectors.toMap(
                                CaseBlueprint.NonCulpritExclusion::characterId,
                                Function.identity(),
                                (left, right) -> left
                        ));
        for (String nonCulprit : nonCulprits) {
            CaseBlueprint.NonCulpritExclusion exclusion = declared.get(nonCulprit);
            if (exclusion == null) {
                issues.add(ValidationIssue.of(
                        "MISSING_NON_CULPRIT_EXCLUSION",
                        "$.solution.nonCulpritExclusions",
                        nonCulprit
                ));
                continue;
            }
            if (!excludedBy.getOrDefault(nonCulprit, Set.of())
                    .containsAll(exclusion.excludedByClueIds())) {
                issues.add(ValidationIssue.of(
                        "EXCLUSION_EFFECT_MISMATCH",
                        "$.solution.nonCulpritExclusions[" + nonCulprit + "]",
                        exclusion.excludedByClueIds().toString()
                ));
            }
        }
        return List.copyOf(issues);
    }
}
