package com.arcadia.station.ai.template;

import java.util.List;

public record WorldTemplate(
        String templateId,
        String version,
        String locale,
        Setting setting,
        List<CharacterDefinition> characters,
        List<LocationDefinition> locations,
        List<StationSystemDefinition> systems,
        List<EvidenceSourceDefinition> evidenceSources
) {
    public record Setting(
            String name,
            String era,
            String summary,
            List<String> publicFacts,
            List<String> privateFacts,
            List<String> worldInvariants,
            List<String> forbiddenElements
    ) {}

    public record CharacterDefinition(
            String id,
            String displayName,
            String occupation,
            boolean suspect,
            String publicProfile,
            List<String> personalityTraits,
            List<String> skills,
            List<String> physicalAccess,
            List<SystemPermission> systemPermissions,
            List<String> knowledgeDomains,
            List<String> motiveDomains,
            List<RelationshipSeed> relationshipSeeds,
            String privateBackground,
            List<String> forbiddenCapabilities
    ) {}

    public record SystemPermission(String systemId, List<String> allowedOperations) {}

    public record RelationshipSeed(
            String characterId,
            String publicRelation,
            List<String> privatePossibilities
    ) {}

    public record LocationDefinition(
            String id,
            String displayName,
            String publicDescription,
            String accessCondition,
            List<String> connectedLocationIds,
            List<String> installedSystemIds,
            List<String> investigableObjectTypes,
            List<String> evidenceSourceTypes
    ) {}

    public record StationSystemDefinition(
            String id,
            String displayName,
            List<String> responsibleRoles,
            List<String> accessibleCharacterIds,
            List<String> allowedOperations,
            List<String> dependentSystemIds,
            List<String> auditSourceTypes,
            List<String> limitations
    ) {}

    public record EvidenceSourceDefinition(String type, List<String> requiredMetadataKeys) {}
}
