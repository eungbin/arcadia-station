package com.arcadia.station.integration;

import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
import com.arcadia.station.ai.template.TemplateRepository;
import com.arcadia.station.ai.template.WorldTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class FrontendIntegrationContractRepository {

    private static final String RESOURCE = "integration/frontend-contract-v1.json";
    private static final Set<String> EXPECTED_NPC_IDS = Set.of(
            "NPC_MAYA",
            "NPC_JUNHO",
            "NPC_SOPHIA",
            "NPC_KASIM",
            "NPC_YUNA"
    );
    private static final Set<String> EXPECTED_THEORY_FIELDS = Set.of(
            "method",
            "trace",
            "motive"
    );

    private final FrontendIntegrationContract contract;

    public FrontendIntegrationContractRepository(
            ObjectMapper objectMapper,
            TemplateRepository templates
    ) {
        this.contract = read(objectMapper);
        validate(contract, templates);
    }

    public FrontendIntegrationContract contract() {
        return contract;
    }

    private FrontendIntegrationContract read(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(
                    new ClassPathResource(RESOURCE).getInputStream(),
                    FrontendIntegrationContract.class
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot load frontend integration contract: " + RESOURCE,
                    exception
            );
        }
    }

    private void validate(
            FrontendIntegrationContract candidate,
            TemplateRepository templates
    ) {
        require(
                "eungbin/arcadia-station".equals(candidate.frontendRepository()),
                "Unexpected frontend repository"
        );
        require(
                EXPECTED_NPC_IDS.equals(candidate.npcCharacterIds().keySet()),
                "Frontend NPC IDs are incomplete"
        );
        require(
                EXPECTED_THEORY_FIELDS.equals(candidate.theoryFields().keySet()),
                "Frontend theory fields are incomplete"
        );

        Set<String> characterIds = templates.world().characters().stream()
                .filter(WorldTemplate.CharacterDefinition::suspect)
                .map(WorldTemplate.CharacterDefinition::id)
                .collect(Collectors.toSet());
        require(
                characterIds.equals(new HashSet<>(candidate.npcCharacterIds().values())),
                "Frontend NPC aliases do not cover the AI suspect IDs exactly"
        );

        Set<String> locationIds = templates.world().locations().stream()
                .map(WorldTemplate.LocationDefinition::id)
                .collect(Collectors.toSet());
        require(
                candidate.investigationObjects().size() == 16,
                "Frontend investigation object map must contain 16 objects"
        );
        for (Map.Entry<String, FrontendIntegrationContract.InvestigationObjectRoute> entry
                : candidate.investigationObjects().entrySet()) {
            require(!entry.getKey().isBlank(), "Blank frontend object ID");
            FrontendIntegrationContract.InvestigationObjectRoute route = entry.getValue();
            require(route.mode() != null, "Missing inspection mode: " + entry.getKey());
            require(
                    locationIds.contains(route.locationId()),
                    "Unknown AI location for " + entry.getKey() + ": " + route.locationId()
            );
            if (route.mode() == FrontendIntegrationContract.InspectionMode.RAG) {
                require(
                        route.query() != null && !route.query().isBlank(),
                        "RAG route requires a query: " + entry.getKey()
                );
            }
        }

        Set<EvidenceRole> mappedRoles = candidate.theoryFields().values().stream()
                .flatMap(java.util.Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        require(
                mappedRoles.equals(
                        new LinkedHashSet<>(templates.rules().finalReportRules().requiredRoles())
                ),
                "Frontend theory fields do not cover every required AI evidence role"
        );
        long mappedRoleCount = candidate.theoryFields().values().stream()
                .mapToLong(java.util.Collection::size)
                .sum();
        require(
                mappedRoleCount == mappedRoles.size(),
                "An AI evidence role is mapped more than once"
        );
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(
                    "Invalid frontend integration contract: " + message
            );
        }
    }
}
