package com.arcadia.station.dto.response;

import java.util.List;

/**
 * 프론트에 노출되는 플레이어 공개 상태. CaseBlueprint를 직접 반환하지 않고
 * 발견한 단서만 걸러서 담는다(스펙 10장 보안 경계).
 */
public record PlayerCaseView(
    String sessionId,
    String status,
    String title,
    String briefing,
    List<PlayerClueView> discoveredClues
) {}
