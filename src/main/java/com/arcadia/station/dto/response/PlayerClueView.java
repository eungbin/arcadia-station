package com.arcadia.station.dto.response;

import com.arcadia.station.domain.caseblueprint.ClueType;

public record PlayerClueView(String clueId, String title, ClueType clueType, String playerText) {}
