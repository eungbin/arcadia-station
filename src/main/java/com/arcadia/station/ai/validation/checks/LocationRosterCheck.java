package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.ArcadiaLocationRoster;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(12)
public class LocationRosterCheck implements CaseBlueprintCheck {

    private static final String CODE = "NON_ROSTER_LOCATION_ID";

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        require(
                blueprint.method().setupAction().locationId(),
                "$.method.setupAction.locationId",
                issues
        );
        require(
                blueprint.method().triggerAction().locationId(),
                "$.method.triggerAction.locationId",
                issues
        );
        blueprint.timeline().forEach(event -> require(
                event.locationId(),
                "$.timeline[" + event.eventId() + "].locationId",
                issues
        ));
        blueprint.clues().forEach(clue -> {
            if (clue.acquisition().locationId() != null) {
                require(
                        clue.acquisition().locationId(),
                        "$.clues[" + clue.clueId() + "].acquisition.locationId",
                        issues
                );
            }
        });
        blueprint.evidenceRecords().forEach(record -> {
            String locationId = record.metadata().get("locationId");
            if (locationId != null && !locationId.isBlank()) {
                require(
                        locationId,
                        "$.evidenceRecords[" + record.recordId() + "].metadata.locationId",
                        issues
                );
            }
        });
        return List.copyOf(issues);
    }

    private void require(
            String locationId,
            String path,
            List<ValidationIssue> issues
    ) {
        if (!ArcadiaLocationRoster.contains(locationId)) {
            issues.add(ValidationIssue.of(CODE, path, String.valueOf(locationId)));
        }
    }
}
