package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import com.arcadia.station.integration.FrontendIntegrationContract;
import com.arcadia.station.integration.FrontendIntegrationContractRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ExplorationObjectCoverageCheck implements CaseBlueprintCheck {

    private final Map<String, FrontendIntegrationContract.InvestigationObjectRoute> objects;

    public ExplorationObjectCoverageCheck(
            FrontendIntegrationContractRepository frontendContracts
    ) {
        this.objects = Map.copyOf(frontendContracts.contract().investigationObjects());
    }

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, Integer> clueCounts = new HashMap<>();

        blueprint.clues().stream()
                .filter(clue -> clue.acquisition().type()
                        == CaseBlueprint.AcquisitionType.EXPLORE)
                .forEach(clue -> validateExploreClue(clue, clueCounts, issues));

        objects.forEach((objectId, route) -> {
            if (route.clueRequired() && clueCounts.getOrDefault(objectId, 0) == 0) {
                issues.add(ValidationIssue.of(
                        "MISSING_REQUIRED_OBJECT_CLUE",
                        "$.clues",
                        objectId
                ));
            }
        });
        return List.copyOf(issues);
    }

    private void validateExploreClue(
            CaseBlueprint.Clue clue,
            Map<String, Integer> clueCounts,
            List<ValidationIssue> issues
    ) {
        String objectId = clue.source().sourceId();
        FrontendIntegrationContract.InvestigationObjectRoute route = objects.get(objectId);
        String path = "$.clues[" + clue.clueId() + "]";
        if (route == null) {
            issues.add(ValidationIssue.of(
                    "UNKNOWN_EXPLORATION_OBJECT_ID",
                    path + ".source.sourceId",
                    objectId
            ));
            return;
        }
        clueCounts.merge(objectId, 1, Integer::sum);
        if (!"PHYSICAL_OBJECT".equals(clue.source().sourceType())) {
            issues.add(ValidationIssue.of(
                    "EXPLORE_OBJECT_SOURCE_TYPE_MISMATCH",
                    path + ".source.sourceType",
                    clue.source().sourceType()
            ));
        }
        if (!Objects.equals(route.locationId(), clue.acquisition().locationId())) {
            issues.add(ValidationIssue.of(
                    "EXPLORE_OBJECT_LOCATION_MISMATCH",
                    path + ".acquisition.locationId",
                    objectId + ":" + clue.acquisition().locationId()
            ));
        }
    }
}
