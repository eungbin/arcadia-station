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
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class ClueGraphCheck implements CaseBlueprintCheck {

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, CaseBlueprint.Clue> clues = blueprint.clues().stream()
                .collect(Collectors.toMap(CaseBlueprint.Clue::clueId, clue -> clue));
        Map<String, Integer> states = new HashMap<>();
        Map<String, Integer> depths = new HashMap<>();

        for (CaseBlueprint.Clue clue : blueprint.clues()) {
            if (!rules.clueRules().allowedAcquisitionTypes().contains(clue.acquisition().type())) {
                issues.add(ValidationIssue.of(
                        "ACQUISITION_TYPE_NOT_ALLOWED",
                        "$.clues[" + clue.clueId() + "].acquisition.type",
                        clue.acquisition().type().name()
                ));
            }
            if (clue.isCore()
                    && clue.acquisition().type() == CaseBlueprint.AcquisitionType.INTERROGATE
                    && clue.acquisition().requiredClueIds().isEmpty()
                    && rules.clueRules().mandatoryFactsRequireDeterministicPath()) {
                issues.add(ValidationIssue.of(
                        "MANDATORY_CLUE_AI_ONLY",
                        "$.clues[" + clue.clueId() + "].acquisition",
                        "Core interrogation clues require deterministic evidence presentation"
                ));
            }
            if (clue.acquisition().type() == CaseBlueprint.AcquisitionType.RAG_QUERY
                    && clue.acquisition().queryTopics().isEmpty()) {
                issues.add(ValidationIssue.of(
                        "RAG_QUERY_TOPICS_MISSING",
                        "$.clues[" + clue.clueId() + "].acquisition.queryTopics",
                        clue.clueId()
                ));
            }
            if (clue.acquisition().type() == CaseBlueprint.AcquisitionType.EXPLORE
                    && clue.acquisition().locationId() == null) {
                issues.add(ValidationIssue.of(
                        "EXPLORE_LOCATION_MISSING",
                        "$.clues[" + clue.clueId() + "].acquisition.locationId",
                        clue.clueId()
                ));
            }
            if (clue.acquisition().type() == CaseBlueprint.AcquisitionType.CONNECT
                    && clue.acquisition().requiredClueIds().size() < 2) {
                issues.add(ValidationIssue.of(
                        "CONNECT_REQUIRES_TWO_CLUES",
                        "$.clues[" + clue.clueId() + "].acquisition.requiredClueIds",
                        clue.clueId()
                ));
            }
            visit(clue.clueId(), clues, states, depths, issues);
        }

        depths.forEach((clueId, depth) -> {
            if (depth > rules.clueRules().maxPrerequisiteDepth()) {
                issues.add(ValidationIssue.of(
                        "CLUE_GRAPH_TOO_DEEP",
                        "$.clues[" + clueId + "].acquisition.requiredClueIds",
                        String.valueOf(depth)
                ));
            }
        });

        Set<String> reachable = new HashSet<>();
        boolean progressed;
        do {
            progressed = false;
            for (CaseBlueprint.Clue clue : blueprint.clues()) {
                if (!reachable.contains(clue.clueId())
                        && reachable.containsAll(clue.acquisition().requiredClueIds())) {
                    reachable.add(clue.clueId());
                    progressed = true;
                }
            }
        } while (progressed);

        blueprint.clues().stream()
                .filter(CaseBlueprint.Clue::isCore)
                .filter(clue -> !reachable.contains(clue.clueId()))
                .forEach(clue -> issues.add(ValidationIssue.of(
                        "MANDATORY_CLUE_UNREACHABLE",
                        "$.clues[" + clue.clueId() + "]",
                        clue.clueId()
                )));

        Set<String> revealedFacts = blueprint.clues().stream()
                .flatMap(clue -> clue.revealsFactIds().stream())
                .collect(Collectors.toSet());
        blueprint.redHerrings().stream()
                .filter(CaseBlueprint.RedHerring::mustBeResolvable)
                .filter(red -> red.resolutionFactIds().stream().noneMatch(revealedFacts::contains))
                .forEach(red -> issues.add(ValidationIssue.of(
                        "UNRESOLVED_RED_HERRING",
                        "$.redHerrings[" + red.redHerringId() + "]",
                        red.redHerringId()
                )));
        return List.copyOf(issues);
    }

    private int visit(
            String clueId,
            Map<String, CaseBlueprint.Clue> clues,
            Map<String, Integer> states,
            Map<String, Integer> depths,
            List<ValidationIssue> issues
    ) {
        if (states.getOrDefault(clueId, 0) == 1) {
            issues.add(ValidationIssue.of(
                    "CLUE_GRAPH_CYCLE",
                    "$.clues[" + clueId + "]",
                    clueId
            ));
            return 0;
        }
        if (states.getOrDefault(clueId, 0) == 2) {
            return depths.getOrDefault(clueId, 0);
        }
        states.put(clueId, 1);
        CaseBlueprint.Clue clue = clues.get(clueId);
        int depth = 0;
        if (clue != null) {
            for (String required : clue.acquisition().requiredClueIds()) {
                depth = Math.max(depth, 1 + visit(required, clues, states, depths, issues));
            }
        }
        states.put(clueId, 2);
        depths.put(clueId, depth);
        return depth;
    }
}
