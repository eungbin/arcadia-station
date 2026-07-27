package com.arcadia.station.ai.template;

import com.arcadia.station.ai.casegen.CaseBlueprint.AcquisitionType;
import com.arcadia.station.ai.casegen.CaseBlueprint.ClueType;
import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
import java.util.List;
import java.util.Map;

public record MysteryRuleTemplate(
        String templateId,
        String version,
        CulpritPolicy culpritPolicy,
        List<EvidenceRole> requiredEvidenceRoles,
        List<EvidenceRole> optionalEvidenceRoles,
        ClueRules clueRules,
        SolutionRules solutionRules,
        FinalReportRules finalReportRules,
        GenerationSafety generationSafety
) {
    public enum CulpritPolicyType {
        FIXED,
        RANDOM_FROM_ELIGIBLE
    }

    public record CulpritPolicy(CulpritPolicyType type, String culpritId) {}

    public record IntRange(int min, int max) {}

    public record ClueRules(
            IntRange coreClueCount,
            IntRange redHerringCount,
            Map<ClueType, Integer> minimumByType,
            List<AcquisitionType> allowedAcquisitionTypes,
            int maxPrerequisiteDepth,
            boolean mandatoryFactsRequireDeterministicPath
    ) {}

    public record SolutionRules(
            boolean requireUniqueCulprit,
            boolean requireExplicitExclusionForEveryNonCulprit,
            boolean rejectUnregisteredWorldIds,
            boolean rejectForbiddenCapabilities,
            boolean requireChronologicalConsistency,
            boolean requireEvidenceForEveryRequiredRole
    ) {}

    public record FinalReportRules(
            boolean requireCulprit,
            List<EvidenceRole> requiredRoles,
            boolean allowRetry,
            int maxWrongSubmissions
    ) {}

    public record GenerationSafety(
            boolean fictionalNonActionableMethodOnly,
            boolean forbidExecutableCode,
            boolean forbidNewCharacters,
            boolean forbidNewLocations,
            boolean forbidNewSystems
    ) {}
}
