package com.arcadia.station.dto.response;

/**
 * role은 WEAK_ROLE_EVIDENCE, characterId는 WEAK_EXCLUSION일 때만 채워진다.
 * 둘 다 null이면 WRONG_CULPRIT. 정답 단서 ID·정답 인물은 절대 담지 않는다(9.3절 경계).
 */
public record MissingLogicItem(String code, String role, String characterId, String message) {}
