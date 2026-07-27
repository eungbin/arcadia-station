package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class WorldReferenceCheck implements CaseBlueprintCheck {

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> characters = ids(world.characters().stream()
                .map(WorldTemplate.CharacterDefinition::id).toList());
        Set<String> locations = ids(world.locations().stream()
                .map(WorldTemplate.LocationDefinition::id).toList());
        Set<String> systems = ids(world.systems().stream()
                .map(WorldTemplate.StationSystemDefinition::id).toList());
        Set<String> sourceTypes = ids(world.evidenceSources().stream()
                .map(WorldTemplate.EvidenceSourceDefinition::type).toList());

        checkTemplateRef(blueprint.worldTemplate(), world.templateId(), world.version(),
                "$.worldTemplate", issues);
        checkTemplateRef(blueprint.ruleTemplate(), rules.templateId(), rules.version(),
                "$.ruleTemplate", issues);
        require(characters, blueprint.culpritId(), "UNKNOWN_CHARACTER_ID", "$.culpritId", issues);
        if (rules.culpritPolicy().type() == MysteryRuleTemplate.CulpritPolicyType.FIXED
                && !rules.culpritPolicy().culpritId().equals(blueprint.culpritId())) {
            issues.add(ValidationIssue.of(
                    "CULPRIT_POLICY_MISMATCH",
                    "$.culpritId",
                    blueprint.culpritId()
            ));
        }
        if (!blueprint.culpritId().equals(blueprint.method().setupAction().actorId())
                || !blueprint.culpritId().equals(blueprint.method().triggerAction().actorId())) {
            issues.add(ValidationIssue.of(
                    "CULPRIT_ACTION_MISMATCH",
                    "$.method",
                    "Setup and trigger actions must be performed by the culprit"
            ));
        }

        checkAction(blueprint.method().setupAction(), characters, locations, systems,
                "$.method.setupAction", issues);
        checkAction(blueprint.method().triggerAction(), characters, locations, systems,
                "$.method.triggerAction", issues);

        Set<String> eventIds = unique(
                blueprint.timeline().stream().map(CaseBlueprint.TimelineEvent::eventId).toList(),
                "$.timeline",
                issues
        );
        Set<String> factIds = unique(
                blueprint.facts().stream().map(CaseBlueprint.Fact::factId).toList(),
                "$.facts",
                issues
        );
        Set<String> clueIds = unique(
                blueprint.clues().stream().map(CaseBlueprint.Clue::clueId).toList(),
                "$.clues",
                issues
        );
        Set<String> recordIds = unique(
                blueprint.evidenceRecords().stream()
                        .map(CaseBlueprint.EvidenceRecord::recordId).toList(),
                "$.evidenceRecords",
                issues
        );
        unique(
                blueprint.redHerrings().stream()
                        .map(CaseBlueprint.RedHerring::redHerringId).toList(),
                "$.redHerrings",
                issues
        );
        if (eventIds.isEmpty()) {
            issues.add(ValidationIssue.of("EMPTY_TIMELINE", "$.timeline", "Timeline is empty"));
        }

        blueprint.timeline().forEach(event -> {
            event.actorIds().forEach(id -> require(
                    characters, id, "UNKNOWN_CHARACTER_ID",
                    "$.timeline[" + event.eventId() + "].actorIds", issues));
            require(locations, event.locationId(), "UNKNOWN_LOCATION_ID",
                    "$.timeline[" + event.eventId() + "].locationId", issues);
            event.factIds().forEach(id -> require(
                    factIds, id, "UNKNOWN_FACT_ID",
                    "$.timeline[" + event.eventId() + "].factIds", issues));
        });
        blueprint.facts().forEach(fact -> fact.subjectCharacterIds().forEach(id -> require(
                characters, id, "UNKNOWN_CHARACTER_ID",
                "$.facts[" + fact.factId() + "].subjectCharacterIds", issues)));
        blueprint.alibis().forEach(alibi -> {
            require(characters, alibi.characterId(), "UNKNOWN_CHARACTER_ID",
                    "$.alibis.characterId", issues);
            alibi.supportingFactIds().forEach(id -> require(
                    factIds, id, "UNKNOWN_FACT_ID", "$.alibis.supportingFactIds", issues));
            alibi.contradictingFactIds().forEach(id -> require(
                    factIds, id, "UNKNOWN_FACT_ID", "$.alibis.contradictingFactIds", issues));
        });
        Set<String> alibiCharacters = blueprint.alibis().stream()
                .map(CaseBlueprint.Alibi::characterId)
                .collect(java.util.stream.Collectors.toSet());
        world.characters().stream()
                .filter(WorldTemplate.CharacterDefinition::suspect)
                .map(WorldTemplate.CharacterDefinition::id)
                .filter(id -> !alibiCharacters.contains(id))
                .forEach(id -> issues.add(ValidationIssue.of(
                        "MISSING_SUSPECT_ALIBI",
                        "$.alibis",
                        id
                )));

        blueprint.clues().forEach(clue -> {
            if (!sourceTypes.contains(clue.source().sourceType())) {
                boolean physical = "PHYSICAL_OBJECT".equals(clue.source().sourceType());
                if (!physical) {
                    issues.add(ValidationIssue.of(
                            "UNKNOWN_EVIDENCE_SOURCE",
                            "$.clues[" + clue.clueId() + "].source.sourceType",
                            clue.source().sourceType()
                    ));
                }
            }
            if (clue.acquisition().locationId() != null) {
                require(locations, clue.acquisition().locationId(), "UNKNOWN_LOCATION_ID",
                        "$.clues[" + clue.clueId() + "].acquisition.locationId", issues);
            }
            if (clue.acquisition().characterId() != null) {
                require(characters, clue.acquisition().characterId(), "UNKNOWN_CHARACTER_ID",
                        "$.clues[" + clue.clueId() + "].acquisition.characterId", issues);
            }
            clue.acquisition().requiredClueIds().forEach(id -> require(
                    clueIds, id, "UNKNOWN_CLUE_ID",
                    "$.clues[" + clue.clueId() + "].acquisition.requiredClueIds", issues));
            clue.revealsFactIds().forEach(id -> require(
                    factIds, id, "UNKNOWN_FACT_ID",
                    "$.clues[" + clue.clueId() + "].revealsFactIds", issues));
            clue.suspectEffects().forEach(effect -> require(
                    characters, effect.characterId(), "UNKNOWN_CHARACTER_ID",
                    "$.clues[" + clue.clueId() + "].suspectEffects", issues));
            if (clue.source().sourceId() != null
                    && !"PHYSICAL_OBJECT".equals(clue.source().sourceType())) {
                require(recordIds, clue.source().sourceId(), "UNKNOWN_RECORD_ID",
                        "$.clues[" + clue.clueId() + "].source.sourceId", issues);
            }
        });

        blueprint.evidenceRecords().forEach(record -> {
            if (!sourceTypes.contains(record.recordType())) {
                issues.add(ValidationIssue.of(
                        "UNKNOWN_EVIDENCE_SOURCE",
                        "$.evidenceRecords[" + record.recordId() + "].recordType",
                        record.recordType()
                ));
            }
            record.revealsClueIds().forEach(id -> require(
                    clueIds, id, "UNKNOWN_CLUE_ID",
                    "$.evidenceRecords[" + record.recordId() + "].revealsClueIds", issues));
        });
        blueprint.npcKnowledge().forEach(npc -> {
            require(characters, npc.characterId(), "UNKNOWN_CHARACTER_ID",
                    "$.npcKnowledge.characterId", issues);
            List<String> allFactRefs = new ArrayList<>();
            allFactRefs.addAll(npc.knownFactIds());
            allFactRefs.addAll(npc.initialClaimFactIds());
            allFactRefs.addAll(npc.concealedFactIds());
            allFactRefs.addAll(npc.allowedLieFactIds());
            npc.revealPolicies().forEach(policy -> {
                allFactRefs.add(policy.factId());
                policy.requiredPresentedClueIds().forEach(id -> require(
                        clueIds, id, "UNKNOWN_CLUE_ID",
                        "$.npcKnowledge.revealPolicies.requiredPresentedClueIds", issues));
            });
            allFactRefs.forEach(id -> require(
                    factIds, id, "UNKNOWN_FACT_ID", "$.npcKnowledge.factIds", issues));
        });
        blueprint.redHerrings().forEach(red -> {
            require(characters, red.suspectId(), "UNKNOWN_CHARACTER_ID",
                    "$.redHerrings.suspectId", issues);
            red.resolutionFactIds().forEach(id -> require(
                    factIds, id, "UNKNOWN_FACT_ID", "$.redHerrings.resolutionFactIds", issues));
        });
        if (!blueprint.culpritId().equals(blueprint.solution().culpritId())) {
            issues.add(ValidationIssue.of(
                    "SOLUTION_CULPRIT_MISMATCH",
                    "$.solution.culpritId",
                    blueprint.solution().culpritId()
            ));
        }
        blueprint.solution().requiredEvidenceByRole().values().stream()
                .flatMap(List::stream)
                .forEach(id -> require(
                        clueIds, id, "UNKNOWN_CLUE_ID",
                        "$.solution.requiredEvidenceByRole", issues));
        blueprint.solution().acceptedAlternativesByRole().values().stream()
                .flatMap(List::stream)
                .forEach(id -> require(
                        clueIds, id, "UNKNOWN_CLUE_ID",
                        "$.solution.acceptedAlternativesByRole", issues));
        blueprint.solution().nonCulpritExclusions().forEach(exclusion -> {
            require(characters, exclusion.characterId(), "UNKNOWN_CHARACTER_ID",
                    "$.solution.nonCulpritExclusions.characterId", issues);
            exclusion.excludedByClueIds().forEach(id -> require(
                    clueIds, id, "UNKNOWN_CLUE_ID",
                    "$.solution.nonCulpritExclusions.excludedByClueIds", issues));
        });
        return List.copyOf(issues);
    }

    private Set<String> ids(List<String> values) {
        return new HashSet<>(values);
    }

    private Set<String> unique(
            List<String> values,
            String path,
            List<ValidationIssue> issues
    ) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> {
            if (!result.add(value)) {
                issues.add(ValidationIssue.of("DUPLICATE_CASE_ID", path, value));
            }
        });
        return result;
    }

    private void checkTemplateRef(
            CaseBlueprint.TemplateRef ref,
            String expectedId,
            String expectedVersion,
            String path,
            List<ValidationIssue> issues
    ) {
        if (!expectedId.equals(ref.id()) || !expectedVersion.equals(ref.version())) {
            issues.add(ValidationIssue.of(
                    "TEMPLATE_VERSION_MISMATCH",
                    path,
                    ref.id() + ":" + ref.version()
            ));
        }
    }

    private void checkAction(
            CaseBlueprint.CaseAction action,
            Set<String> characters,
            Set<String> locations,
            Set<String> systems,
            String path,
            List<ValidationIssue> issues
    ) {
        require(characters, action.actorId(), "UNKNOWN_CHARACTER_ID", path + ".actorId", issues);
        require(locations, action.locationId(), "UNKNOWN_LOCATION_ID", path + ".locationId", issues);
        require(systems, action.systemId(), "UNKNOWN_SYSTEM_ID", path + ".systemId", issues);
    }

    private void require(
            Set<String> valid,
            String actual,
            String code,
            String path,
            List<ValidationIssue> issues
    ) {
        if (!valid.contains(actual)) {
            issues.add(ValidationIssue.of(code, path, String.valueOf(actual)));
        }
    }
}
