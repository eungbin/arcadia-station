package com.arcadia.station.domain;

public enum SessionState {
    CREATING,
    VALIDATING,
    READY,
    BRIEFING,
    INVESTIGATION,
    DEDUCTION,
    COMPLETED,
    // 요청 C(FRONTEND_BACKEND_CLUE_CONTEXT_REQUEST_2026-08-06.md 5절): 오답 소진으로 게임이 끝난 상태.
    // FAILED는 "사건 생성 자체 실패"라는 별도 계약(api-spec.md)이라 재사용하지 않는다.
    INCORRECT,
    FAILED
}
