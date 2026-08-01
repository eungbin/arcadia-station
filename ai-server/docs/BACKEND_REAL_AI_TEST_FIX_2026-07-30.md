# 백엔드 real-ai 통합 테스트 신규 발견 대응

> 확인일: 2026-07-30
> 대상: EXPLORE 단서를 NPC에게 제시할 때 발생한 `400 INVALID_REQUEST`

## 결론

백엔드의 재현과 원인 분석이 정확합니다. 실제 `real-ai` 구조에서는 백엔드가
`EXPLORE`/`CONNECT` 단서 상태를 관리하지만, AI 서버가 자신의 메모리
`EvidenceInventory`를 다시 검사해 정상적으로 발견한 단서를 거절하고 있었습니다.

AI 단독 통합 테스트는 AI 서버의 `/explore` API를 직접 호출해 양쪽 인벤토리가
우연히 일치했기 때문에 이 서비스 경계 문제를 발견하지 못했습니다.

## 확정 계약

- 게임 진행과 발견 단서의 기준 데이터는 백엔드 `EvidenceInventory`입니다.
- 백엔드는 NPC에게 제시한 발견 단서의 누적 목록을 `presentedClueIds`로 보냅니다.
- AI 서버는 `presentedClueIds`가 동결된 CaseBlueprint에 존재하는지만 검사합니다.
- 백엔드가 단독 해금한 `EXPLORE`/`CONNECT` 단서도 별도 동기화 없이 제시할 수 있습니다.
- CaseBlueprint에 없는 ID는 계속 `400 INVALID_REQUEST`로 거절합니다.
- 사건 생성·조회, NPC, RAG는 동일한 `X-Internal-AI-Key`를 검사합니다.

요청 필드는 바뀌지 않습니다.

```json
{
  "question": "제시한 두 기록을 설명해 주세요.",
  "presentedClueIds": [
    "CLUE-TRIGGER-LOG",
    "CLUE-SETUP-PANEL"
  ]
}
```

## 추가한 회귀 검증

실제 서비스 경계와 동일하게 다음 순서로 검증합니다.

1. 백엔드용 내부 사건 생성 후 `READY` 대기
2. AI 서버 `/explore`는 호출하지 않음
3. RAG로 `CLUE-TRIGGER-LOG` 해금
4. RAG 단서와 백엔드 발견 EXPLORE 단서 `CLUE-SETUP-PANEL`을 함께 NPC에게 제시
5. 심문 `200`과 `FACT-SETUP` 공개 확인
6. CaseBlueprint에 없는 단서는 `400 INVALID_REQUEST` 확인
7. NPC/RAG에서 내부 키 누락 시 `403 INVALID_INTERNAL_API_KEY` 확인

## 검증 결과

- 집중 회귀 테스트: `GameFlowIntegrationTest` 4개 통과
- Maven 전체 테스트: 24개 통과
- 실패: 0
- 오류: 0
- 건너뜀: 0
- 실제 HTTP 오프라인 스모크 테스트:
  `health=UP`, 사건 `READY`, RAG 단서 `CLUE-TRIGGER-LOG`,
  EXPLORE 단서 포함 NPC 심문 `200`과 `FACT-SETUP`, 내부 키 누락 `403`

백엔드의 AI 호출 실패 안전 응답은 네트워크 장애와 AI 서버 재시작에 대비해 그대로
유지하는 것을 권장합니다.
