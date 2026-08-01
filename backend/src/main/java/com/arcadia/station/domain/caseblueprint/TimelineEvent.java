package com.arcadia.station.domain.caseblueprint;

import java.util.List;

public record TimelineEvent(
    String eventId,
    String time,
    List<String> actorIds,
    String locationId,
    TimelineActionType actionType,
    String summary,
    List<String> factIds
) {}
