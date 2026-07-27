package com.arcadia.station.ai.validation;

import com.arcadia.station.ai.template.WorldTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorldTemplateValidator {

    public List<ValidationIssue> validate(WorldTemplate world) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (world == null) {
            return List.of(ValidationIssue.of("WORLD_TEMPLATE_INVALID", "$", "Template is null"));
        }

        Set<String> characterIds = unique(
                world.characters().stream().map(WorldTemplate.CharacterDefinition::id).toList(),
                "$.characters",
                issues
        );
        Set<String> locationIds = unique(
                world.locations().stream().map(WorldTemplate.LocationDefinition::id).toList(),
                "$.locations",
                issues
        );
        Set<String> systemIds = unique(
                world.systems().stream().map(WorldTemplate.StationSystemDefinition::id).toList(),
                "$.systems",
                issues
        );
        Map<String, WorldTemplate.StationSystemDefinition> systemsById =
                world.systems().stream().collect(Collectors.toMap(
                        WorldTemplate.StationSystemDefinition::id,
                        Function.identity()
                ));
        unique(
                world.evidenceSources().stream().map(WorldTemplate.EvidenceSourceDefinition::type).toList(),
                "$.evidenceSources",
                issues
        );

        if (world.characters().stream().filter(WorldTemplate.CharacterDefinition::suspect).count() < 3) {
            issues.add(ValidationIssue.of(
                    "WORLD_MINIMUM_SUSPECTS",
                    "$.characters",
                    "At least three suspects are required"
            ));
        }
        if (world.locations().isEmpty()) {
            issues.add(ValidationIssue.of(
                    "WORLD_MINIMUM_LOCATIONS",
                    "$.locations",
                    "At least one investigation location is required"
            ));
        }

        for (WorldTemplate.CharacterDefinition character : world.characters()) {
            checkRefs(character.physicalAccess(), locationIds, "UNKNOWN_LOCATION_ID",
                    "$.characters[" + character.id() + "].physicalAccess", issues);
            for (WorldTemplate.SystemPermission permission : character.systemPermissions()) {
                if (!systemIds.contains(permission.systemId())) {
                    issues.add(ValidationIssue.of(
                            "UNKNOWN_SYSTEM_ID",
                            "$.characters[" + character.id() + "].systemPermissions",
                            permission.systemId()
                    ));
                } else {
                    WorldTemplate.StationSystemDefinition system =
                            systemsById.get(permission.systemId());
                    if (!system.accessibleCharacterIds().contains(character.id())) {
                        issues.add(ValidationIssue.of(
                                "SYSTEM_ACCESS_LIST_MISMATCH",
                                "$.characters[" + character.id() + "].systemPermissions",
                                permission.systemId()
                        ));
                    }
                    permission.allowedOperations().stream()
                            .filter(operation -> !system.allowedOperations().contains(operation))
                            .forEach(operation -> issues.add(ValidationIssue.of(
                                    "UNKNOWN_SYSTEM_OPERATION",
                                    "$.characters[" + character.id() + "].systemPermissions",
                                    permission.systemId() + ":" + operation
                            )));
                }
                if (new HashSet<>(permission.allowedOperations()).size()
                        != permission.allowedOperations().size()) {
                    issues.add(ValidationIssue.of(
                            "DUPLICATE_OPERATION",
                            "$.characters[" + character.id() + "].systemPermissions",
                            permission.systemId()
                    ));
                }
            }
            for (WorldTemplate.RelationshipSeed relationship : character.relationshipSeeds()) {
                if (!characterIds.contains(relationship.characterId())) {
                    issues.add(ValidationIssue.of(
                            "UNKNOWN_CHARACTER_ID",
                            "$.characters[" + character.id() + "].relationshipSeeds",
                            relationship.characterId()
                    ));
                }
            }
            Set<String> granted = new HashSet<>(character.skills());
            character.systemPermissions().forEach(permission -> granted.addAll(permission.allowedOperations()));
            character.forbiddenCapabilities().stream()
                    .filter(granted::contains)
                    .forEach(capability -> issues.add(ValidationIssue.of(
                            "FORBIDDEN_CAPABILITY_GRANTED",
                            "$.characters[" + character.id() + "]",
                            capability
                    )));
        }

        for (WorldTemplate.LocationDefinition location : world.locations()) {
            checkRefs(location.connectedLocationIds(), locationIds, "UNKNOWN_LOCATION_ID",
                    "$.locations[" + location.id() + "].connectedLocationIds", issues);
            checkRefs(location.installedSystemIds(), systemIds, "UNKNOWN_SYSTEM_ID",
                    "$.locations[" + location.id() + "].installedSystemIds", issues);
        }

        for (WorldTemplate.StationSystemDefinition system : world.systems()) {
            checkRefs(system.accessibleCharacterIds(), characterIds, "UNKNOWN_CHARACTER_ID",
                    "$.systems[" + system.id() + "].accessibleCharacterIds", issues);
            checkRefs(system.dependentSystemIds(), systemIds, "UNKNOWN_SYSTEM_ID",
                    "$.systems[" + system.id() + "].dependentSystemIds", issues);
            if (new HashSet<>(system.allowedOperations()).size() != system.allowedOperations().size()) {
                issues.add(ValidationIssue.of(
                        "DUPLICATE_OPERATION",
                        "$.systems[" + system.id() + "].allowedOperations",
                        system.id()
                ));
            }
        }

        if (world.version() == null || !world.version().matches("\\d+\\.\\d+\\.\\d+")) {
            issues.add(ValidationIssue.of(
                    "INVALID_TEMPLATE_VERSION",
                    "$.version",
                    String.valueOf(world.version())
            ));
        }
        return List.copyOf(issues);
    }

    private Set<String> unique(
            List<String> ids,
            String path,
            List<ValidationIssue> issues
    ) {
        Set<String> unique = new HashSet<>();
        ids.forEach(id -> {
            if (id == null || id.isBlank()) {
                issues.add(ValidationIssue.of("BLANK_ID", path, "ID cannot be blank"));
            } else if (!unique.add(id)) {
                issues.add(ValidationIssue.of("DUPLICATE_ID", path, id));
            }
        });
        return unique;
    }

    private void checkRefs(
            List<String> references,
            Set<String> validIds,
            String code,
            String path,
            List<ValidationIssue> issues
    ) {
        references.stream()
                .filter(id -> !validIds.contains(id))
                .forEach(id -> issues.add(ValidationIssue.of(code, path, id)));
    }
}
