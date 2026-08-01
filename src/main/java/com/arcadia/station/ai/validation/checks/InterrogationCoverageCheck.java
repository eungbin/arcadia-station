package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(15)
public class InterrogationCoverageCheck implements CaseBlueprintCheck {

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> suspects = world.characters().stream()
                .filter(WorldTemplate.CharacterDefinition::suspect)
                .map(WorldTemplate.CharacterDefinition::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> alibiIds = uniqueCharacterIds(
                blueprint.alibis().stream().map(CaseBlueprint.Alibi::characterId).toList(),
                "DUPLICATE_ALIBI_CHARACTER_ID",
                "$.alibis",
                issues
        );

        if (!suspects.contains(blueprint.culpritId())) {
            issues.add(ValidationIssue.of(
                    "CULPRIT_NOT_SUSPECT",
                    "$.culpritId",
                    blueprint.culpritId()
            ));
        }
        blueprint.alibis().stream()
                .map(CaseBlueprint.Alibi::characterId)
                .filter(id -> !suspects.contains(id))
                .forEach(id -> issues.add(ValidationIssue.of(
                        "NON_SUSPECT_ALIBI",
                        "$.alibis",
                        id
                )));

        Map<String, CaseBlueprint.Alibi> alibisByCharacter = blueprint.alibis().stream()
                .collect(Collectors.toMap(
                        CaseBlueprint.Alibi::characterId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        Set<String> knowledgeIds = uniqueCharacterIds(
                blueprint.npcKnowledge().stream()
                        .map(CaseBlueprint.NpcKnowledge::characterId)
                        .toList(),
                "DUPLICATE_NPC_KNOWLEDGE_CHARACTER_ID",
                "$.npcKnowledge",
                issues
        );
        alibiIds.stream()
                .filter(id -> !knowledgeIds.contains(id))
                .forEach(id -> issues.add(ValidationIssue.of(
                        "MISSING_NPC_KNOWLEDGE",
                        "$.npcKnowledge",
                        id
                )));

        blueprint.npcKnowledge().forEach(knowledge -> {
            String characterId = knowledge.characterId();
            if (knowledge.initialClaimFactIds().isEmpty()) {
                issues.add(ValidationIssue.of(
                        "MISSING_INITIAL_CLAIM_FACT",
                        "$.npcKnowledge[" + characterId + "].initialClaimFactIds",
                        characterId
                ));
            }
            if (knowledge.recommendedQuestionTopics().isEmpty()) {
                issues.add(ValidationIssue.of(
                        "MISSING_RECOMMENDED_QUESTION_TOPIC",
                        "$.npcKnowledge[" + characterId + "].recommendedQuestionTopics",
                        characterId
                ));
            }

            CaseBlueprint.Alibi alibi = alibisByCharacter.get(characterId);
            if (alibi == null) {
                return;
            }
            Set<String> alibiFactIds = new HashSet<>(alibi.supportingFactIds());
            alibiFactIds.addAll(alibi.contradictingFactIds());
            knowledge.initialClaimFactIds().stream()
                    .filter(id -> !alibiFactIds.contains(id))
                    .forEach(id -> issues.add(ValidationIssue.of(
                            "INITIAL_CLAIM_FACT_NOT_LINKED_TO_ALIBI",
                            "$.npcKnowledge[" + characterId + "].initialClaimFactIds",
                            id
                    )));
        });

        Set<String> expectedNonCulprits = new LinkedHashSet<>(alibiIds);
        expectedNonCulprits.remove(blueprint.culpritId());
        List<String> declaredNonCulprits = blueprint.solution().nonCulpritExclusions().stream()
                .map(CaseBlueprint.NonCulpritExclusion::characterId)
                .toList();
        Set<String> declaredNonCulpritIds = uniqueCharacterIds(
                declaredNonCulprits,
                "DUPLICATE_NON_CULPRIT_EXCLUSION",
                "$.solution.nonCulpritExclusions",
                issues
        );
        expectedNonCulprits.stream()
                .filter(id -> !declaredNonCulpritIds.contains(id))
                .forEach(id -> issues.add(ValidationIssue.of(
                        "MISSING_NON_CULPRIT_EXCLUSION",
                        "$.solution.nonCulpritExclusions",
                        id
                )));
        declaredNonCulpritIds.stream()
                .filter(id -> !expectedNonCulprits.contains(id))
                .forEach(id -> issues.add(ValidationIssue.of(
                        "INVALID_NON_CULPRIT_EXCLUSION",
                        "$.solution.nonCulpritExclusions",
                        id
                )));

        blueprint.clues().forEach(clue -> clue.suspectEffects().stream()
                .map(CaseBlueprint.SuspectEffect::characterId)
                .filter(id -> !alibiIds.contains(id))
                .forEach(id -> issues.add(ValidationIssue.of(
                        "NON_ALIBI_SUSPECT_EFFECT",
                        "$.clues[" + clue.clueId() + "].suspectEffects",
                        id
                ))));

        return List.copyOf(issues);
    }

    private Set<String> uniqueCharacterIds(
            List<String> values,
            String duplicateCode,
            String path,
            List<ValidationIssue> issues
    ) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> {
            if (!result.add(value)) {
                issues.add(ValidationIssue.of(duplicateCode, path, value));
            }
        });
        return result;
    }
}
