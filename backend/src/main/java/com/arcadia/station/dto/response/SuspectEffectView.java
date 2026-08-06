package com.arcadia.station.dto.response;

import com.arcadia.station.domain.caseblueprint.SuspectEffectType;

public record SuspectEffectView(String characterId, SuspectEffectType effect) {}
