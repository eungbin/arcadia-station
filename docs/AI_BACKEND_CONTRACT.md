# AI 서버 ↔ 백엔드 게임 시작 계약

백엔드 개발자 질문:

> 게임 시작할 때 AI 서버에서 응답하는 데이터 어떻게 주시나요?

권장 답변은 **즉시 `202 Accepted`를 반환하고 상태를 폴링하는 비동기 계약**입니다.
사건 생성과 검증이 10초 이상 걸릴 수 있기 때문입니다. 백엔드가 `sessionId`를 먼저
발급하고 AI 서버에 전달하면, 같은 ID로 로그·재시도·동결 사건을 추적할 수 있습니다.

## 1. 생성 작업 시작

```http
POST /internal/v1/cases
Content-Type: application/json
X-Internal-AI-Key: <AI_INTERNAL_API_KEY>
```

요청:

```json
{
  "sessionId": "game_01K1ARCADIA9J2N7P4Q",
  "seed": "optional-replayable-seed"
}
```

- `sessionId`: 백엔드가 만든 8~64자의 영문/숫자/`_`/`-` ID입니다.
- `seed`: 선택입니다. 생략하면 AI 서버가 암호학적 난수 seed를 생성합니다.
- 동일 `sessionId` 재요청은 중복 생성하지 않고 `409`로 거절합니다.

응답 (`202 Accepted`):

```json
{
  "sessionId": "game_01K1ARCADIA9J2N7P4Q",
  "status": "CREATING",
  "statusUrl": "/internal/v1/cases/game_01K1ARCADIA9J2N7P4Q"
}
```

이 첫 응답에는 사건 본문이나 범인 정보가 없습니다.

## 2. 상태 조회

```http
GET /internal/v1/cases/game_01K1ARCADIA9J2N7P4Q
X-Internal-AI-Key: <AI_INTERNAL_API_KEY>
```

생성 중:

```json
{
  "sessionId": "game_01K1ARCADIA9J2N7P4Q",
  "status": "VALIDATING",
  "generation": null,
  "errorCode": null
}
```

완료:

```json
{
  "sessionId": "game_01K1ARCADIA9J2N7P4Q",
  "status": "READY",
  "generation": {
    "blueprintId": "CASE-GAME01K1ARCADIA9J2",
    "seed": "optional-replayable-seed",
    "worldTemplate": {
      "id": "ARCADIA_WORLD",
      "version": "1.0.0"
    },
    "ruleTemplate": {
      "id": "ARCADIA_MYSTERY_RULES",
      "version": "1.0.0"
    },
    "blueprintSha256": "64자리 SHA-256",
    "generationAttemptCount": 1,
    "generationSource": "AI",
    "model": "환경 변수로 선택된 모델",
    "promptVersion": "case-generator-v1",
    "createdAt": "2026-07-28T00:00:00Z",
    "frozenAt": "2026-07-28T00:00:08Z",
    "caseBlueprint": {
      "blueprintId": "CASE-GAME01K1ARCADIA9J2",
      "seed": "optional-replayable-seed",
      "worldTemplate": {
        "id": "ARCADIA_WORLD",
        "version": "1.0.0"
      },
      "ruleTemplate": {
        "id": "ARCADIA_MYSTERY_RULES",
        "version": "1.0.0"
      },
      "culpritId": "SOPHIA",
      "title": "세션별 생성 제목",
      "briefing": "플레이어 공개용 비스포일러 브리핑",
      "truthSummary": "백엔드 전용 사건 진실",
      "method": {},
      "timeline": [],
      "facts": [],
      "alibis": [],
      "clues": [],
      "evidenceRecords": [],
      "npcKnowledge": [],
      "redHerrings": [],
      "solution": {}
    }
  },
  "errorCode": null
}
```

위 `caseBlueprint` 내부의 빈 객체·배열은 필드 종류를 보여주기 위한 축약 표기입니다.
실제 `READY` 응답은 `case-blueprint.schema.json`을 만족하는 전체 사건 데이터로 채워집니다.

`caseBlueprint`에는 다음이 모두 들어갑니다.

- 사건 제목, 브리핑, 전체 진실과 허구적 수법
- 시간순 타임라인과 용의자별 실제 알리바이
- 사실, 핵심 단서, 미끼 단서와 획득 조건
- RAG 검색용 확정 기록
- NPC별 지식·은폐·증거 제시 해금 규칙
- 범인과 `SETUP`/`TRIGGER`/`OPPORTUNITY`/`MOTIVE` 정답 단서 ID
- 비범인별 배제 근거

`generationSource`가 `FALLBACK`이어도 사건은 모든 검증을 통과한 상태이며 `status`는
`FAILED`가 아니라 `READY`입니다. 따라서 백엔드는 정상 게임을 시작하면 됩니다.

## 3. 백엔드 저장·공개 규칙

백엔드는 완료 응답을 원문 JSON 또는 동일 구조로 저장하고 `blueprintSha256`도 함께
저장합니다. 같은 세션에서 다시 생성 요청해 사건을 덮어쓰지 않습니다.

React에는 `caseBlueprint` 전체를 전달하면 안 됩니다. 게임 시작 시 프론트에 공개할
최소 데이터는 다음뿐입니다.

```json
{
  "sessionId": "game_01K1ARCADIA9J2N7P4Q",
  "status": "READY",
  "title": "세션별 생성 제목",
  "briefing": "플레이어 공개용 비스포일러 브리핑",
  "suspects": [
    {
      "id": "SOPHIA",
      "displayName": "소피아 알바레즈",
      "occupation": "의무관",
      "publicProfile": "승무원 건강 관리와 환경 안전 점검을 담당한다."
    }
  ],
  "availableLocations": [
    {
      "id": "LIFE_SUPPORT_CONTROL",
      "displayName": "생명 유지 제어실",
      "publicDescription": "환경 상태를 확인하는 통제 구역이다."
    }
  ],
  "discoveredClues": []
}
```

게임 완료 전 프론트에 보내면 안 되는 필드:

- `culpritId`
- `truthSummary`
- `method`
- `actualWhereabouts`
- 미발견 `clues`
- 전체 `solution`
- `npcKnowledge.concealedFactIds`
- `redHerrings.resolutionFactIds`

## 4. 오류 처리

- `400`: 요청 형식 또는 ID가 잘못됨
- `403`: 내부 API 키 불일치
- `404`: 세션 없음
- `409`: 중복 `sessionId` 또는 잘못된 상태
- `FAILED`: 내장 폴백까지 로드하지 못한 서버 구성 오류

네트워크 타임아웃이나 모델 거부, 구조화 출력 실패, 논리 검증 실패는 최대 두 번
재시도한 뒤 검증 완료 폴백으로 전환하므로 일반적으로 `READY`로 끝납니다.
