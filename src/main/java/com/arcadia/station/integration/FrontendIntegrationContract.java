package com.arcadia.station.integration;

import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
import java.util.List;
import java.util.Map;

public record FrontendIntegrationContract(
        String version,
        String frontendRepository,
        Map<String, String> npcCharacterIds,
        Map<String, InvestigationObjectRoute> investigationObjects,
        Map<String, List<EvidenceRole>> theoryFields
) {
    public enum InspectionMode {
        EXPLORE,
        RAG
    }

    public record InvestigationObjectRoute(
            InspectionMode mode,
            String locationId,
            String query,
            boolean clueRequired
    ) {}
}
