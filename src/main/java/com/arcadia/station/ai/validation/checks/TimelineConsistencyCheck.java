package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class TimelineConsistencyCheck implements CaseBlueprintCheck {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, Set<String>> actorLocationsAtTime = new HashMap<>();
        LocalTime previous = null;
        Map<String, LocalTime> factTimes = new HashMap<>();
        for (CaseBlueprint.TimelineEvent event : blueprint.timeline()) {
            LocalTime time;
            try {
                time = LocalTime.parse(event.time(), TIME);
            } catch (DateTimeParseException ex) {
                issues.add(ValidationIssue.of(
                        "TIMELINE_CONFLICT",
                        "$.timeline[" + event.eventId() + "].time",
                        event.time()
                ));
                continue;
            }
            if (previous != null && time.isBefore(previous)) {
                issues.add(ValidationIssue.of(
                        "TIMELINE_CONFLICT",
                        "$.timeline[" + event.eventId() + "]",
                        "Timeline is not chronological"
                ));
            }
            previous = time;
            event.factIds().forEach(factId -> factTimes.putIfAbsent(factId, time));
            for (String actor : event.actorIds()) {
                String key = actor + "@" + event.time();
                actorLocationsAtTime.computeIfAbsent(key, ignored -> new HashSet<>())
                        .add(event.locationId());
            }
        }
        actorLocationsAtTime.forEach((actorTime, locations) -> {
            if (locations.size() > 1) {
                issues.add(ValidationIssue.of(
                        "TIMELINE_CONFLICT",
                        "$.timeline",
                        actorTime + " appears in multiple locations"
                ));
            }
        });

        LocalTime setup = earliestRoleTime(blueprint, EvidenceRole.SETUP, factTimes);
        LocalTime trigger = earliestRoleTime(blueprint, EvidenceRole.TRIGGER, factTimes);
        if (setup == null || trigger == null || !setup.isBefore(trigger)) {
            issues.add(ValidationIssue.of(
                    "TIMELINE_CONFLICT",
                    "$.timeline",
                    "Setup evidence must precede trigger evidence"
            ));
        }
        return List.copyOf(issues);
    }

    private LocalTime earliestRoleTime(
            CaseBlueprint blueprint,
            EvidenceRole role,
            Map<String, LocalTime> factTimes
    ) {
        Set<String> roleClues = new HashSet<>(
                blueprint.solution().requiredEvidenceByRole().getOrDefault(role, List.of())
        );
        return blueprint.clues().stream()
                .filter(clue -> roleClues.contains(clue.clueId()))
                .flatMap(clue -> clue.revealsFactIds().stream())
                .map(factTimes::get)
                .filter(java.util.Objects::nonNull)
                .min(LocalTime::compareTo)
                .orElse(null);
    }
}
