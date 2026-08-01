package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class CapabilityConsistencyCheck implements CaseBlueprintCheck {

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        Map<String, WorldTemplate.CharacterDefinition> characters = world.characters().stream()
                .collect(Collectors.toMap(WorldTemplate.CharacterDefinition::id, Function.identity()));
        List<ValidationIssue> issues = new ArrayList<>();
        checkAction(characters, blueprint.method().setupAction(), "$.method.setupAction", issues);
        checkAction(characters, blueprint.method().triggerAction(), "$.method.triggerAction", issues);
        return List.copyOf(issues);
    }

    private void checkAction(
            Map<String, WorldTemplate.CharacterDefinition> characters,
            CaseBlueprint.CaseAction action,
            String path,
            List<ValidationIssue> issues
    ) {
        WorldTemplate.CharacterDefinition actor = characters.get(action.actorId());
        if (actor == null) {
            return;
        }
        if (!actor.physicalAccess().contains(action.locationId())) {
            issues.add(ValidationIssue.of(
                    "CAPABILITY_MISMATCH",
                    path + ".locationId",
                    actor.id() + " cannot access " + action.locationId()
            ));
        }
        Set<String> allowedOperations = actor.systemPermissions().stream()
                .filter(permission -> permission.systemId().equals(action.systemId()))
                .flatMap(permission -> permission.allowedOperations().stream())
                .collect(Collectors.toSet());
        if (!allowedOperations.contains(action.operation())) {
            issues.add(ValidationIssue.of(
                    "CAPABILITY_MISMATCH",
                    path + ".operation",
                    actor.id() + " cannot perform " + action.operation()
            ));
        }
        Set<String> skills = new HashSet<>(actor.skills());
        action.requiredCapabilityIds().stream()
                .filter(capability -> !skills.contains(capability))
                .forEach(capability -> issues.add(ValidationIssue.of(
                        "CAPABILITY_MISMATCH",
                        path + ".requiredCapabilityIds",
                        actor.id() + " lacks " + capability
                )));
        action.requiredCapabilityIds().stream()
                .filter(actor.forbiddenCapabilities()::contains)
                .forEach(capability -> issues.add(ValidationIssue.of(
                        "FORBIDDEN_CAPABILITY",
                        path + ".requiredCapabilityIds",
                        capability
                )));
    }
}
