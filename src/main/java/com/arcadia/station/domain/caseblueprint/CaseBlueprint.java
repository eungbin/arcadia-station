package com.arcadia.station.domain.caseblueprint;

import java.util.List;

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
) {}
