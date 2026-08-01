package com.arcadia.station.ai.casegen;

import java.util.List;
import java.util.Map;

public record CaseBlueprint(
        String blueprintId,
        String seed,
        TemplateRef worldTemplate,
        TemplateRef ruleTemplate,
        String culpritId,
        String title,
        String briefing,
        String truthSummary,
        Method method,
        List<TimelineEvent> timeline,
        List<Fact> facts,
        List<Alibi> alibis,
        List<Clue> clues,
        List<EvidenceRecord> evidenceRecords,
        List<NpcKnowledge> npcKnowledge,
        List<RedHerring> redHerrings,
        Solution solution
) {
    public enum EvidenceRole {
        SETUP,
        TRIGGER,
        OPPORTUNITY,
        MOTIVE,
        VICTIM_CONDITION
    }

    public enum ClueType {
        PHYSICAL,
        DIGITAL,
        MOTIVE,
        OPPORTUNITY
    }

    public enum AcquisitionType {
        EXPLORE,
        INTERROGATE,
        RAG_QUERY,
        CONNECT,
        AUTO
    }

    public enum SuspectEffectType {
        SUPPORTS,
        EXCLUDES,
        NEUTRAL
    }

    public enum TimelineActionType {
        MOVEMENT,
        SYSTEM_ACTION,
        CONVERSATION,
        DISCOVERY,
        BACKGROUND
    }

    public enum FactKind {
        ACTION,
        MOTIVE,
        ALIBI,
        EXCLUSION,
        CONDITION,
        CLAIM
    }

    public enum RecordVisibility {
        SEARCHABLE,
        HIDDEN
    }

    public enum ResponseMode {
        DENIAL,
        EVASION,
        PARTIAL_ADMISSION,
        FULL_ADMISSION
    }

    public record TemplateRef(String id, String version) {}

    public record Method(
            String fictionalSummary,
            CaseAction setupAction,
            CaseAction triggerAction,
            String victimCondition
    ) {}

    public record CaseAction(
            String actorId,
            String locationId,
            String systemId,
            String operation,
            List<String> requiredCapabilityIds
    ) {}

    public record TimelineEvent(
            String eventId,
            String time,
            List<String> actorIds,
            String locationId,
            TimelineActionType actionType,
            String summary,
            List<String> factIds
    ) {}

    public record Fact(
            String factId,
            FactKind kind,
            String statement,
            boolean truthValue,
            List<String> subjectCharacterIds
    ) {}

    public record Alibi(
            String characterId,
            String initialClaim,
            String actualWhereabouts,
            List<String> supportingFactIds,
            List<String> contradictingFactIds
    ) {}

    public record Clue(
            String clueId,
            String title,
            ClueType clueType,
            boolean isCore,
            List<EvidenceRole> solutionRoles,
            ClueSource source,
            Acquisition acquisition,
            List<String> revealsFactIds,
            String playerText,
            List<SuspectEffect> suspectEffects
    ) {}

    public record ClueSource(String sourceType, String sourceId) {}

    public record Acquisition(
            AcquisitionType type,
            String locationId,
            String characterId,
            List<String> requiredClueIds,
            List<String> queryTopics
    ) {}

    public record SuspectEffect(String characterId, SuspectEffectType effect) {}

    public record EvidenceRecord(
            String recordId,
            String recordType,
            String timestamp,
            String title,
            String body,
            Map<String, String> metadata,
            List<String> revealsClueIds,
            List<String> searchTerms,
            RecordVisibility visibility
    ) {}

    public record NpcKnowledge(
            String characterId,
            List<String> knownFactIds,
            List<String> initialClaimFactIds,
            List<String> concealedFactIds,
            List<RevealPolicy> revealPolicies,
            List<String> allowedLieFactIds,
            List<String> recommendedQuestionTopics
    ) {}

    public record RevealPolicy(
            String factId,
            List<String> requiredPresentedClueIds,
            ResponseMode responseMode
    ) {}

    public record RedHerring(
            String redHerringId,
            String suspectId,
            String presentation,
            List<String> resolutionFactIds,
            boolean mustBeResolvable
    ) {}

    public record Solution(
            String culpritId,
            Map<EvidenceRole, List<String>> requiredEvidenceByRole,
            Map<EvidenceRole, List<String>> acceptedAlternativesByRole,
            List<NonCulpritExclusion> nonCulpritExclusions
    ) {}

    public record NonCulpritExclusion(
            String characterId,
            List<String> excludedByClueIds,
            String reason
    ) {}
}
