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

### 축약 없는 실제 `READY` 응답

백엔드 DTO 작성에 사용할 전체 샘플은 다음 파일입니다.

[`examples/internal-case-ready.response.json`](./examples/internal-case-ready.response.json)

이 파일은 문서용으로 손으로 조립한 JSON이 아니라 오프라인 모드에서 다음 요청을
보낸 뒤 실제 `InternalCaseController`의 HTTP 응답을 UTF-8로 캡처한 결과입니다.

```json
{
  "sessionId": "backend_contract_001",
  "seed": "backend-seed"
}
```

샘플에는 다음 항목이 축약 없이 들어 있습니다.

- 생성 메타데이터와 SHA-256
- 전체 범행 수법의 준비·촉발 액션
- 타임라인 4건
- 사실 10건과 용의자 5명의 알리바이
- 단서 14건(`EXPLORE` 10건 + `RAG_QUERY` 4건)과 획득 조건·선행 단서·용의자 효과
- RAG 확정 기록 4건과 metadata
- NPC 지식·은폐·증거 제시 해금 정책
- 미끼 단서와 해소 사실
- 네 필수 추리 역할의 정답 단서 및 비범인 배제 근거

샘플의 `createdAt`과 `frozenAt` 값은 캡처 시각이므로 실행할 때마다 달라집니다.
`generationSource`가 `FALLBACK`이고 `generationAttemptCount`가 `0`인 것은 API 키 없는
오프라인 실행 결과이기 때문입니다. 온라인 생성 성공 시에는 각각 `AI`, `1`~`3`이
됩니다. 나머지 구조는 동일합니다.

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
      "version": "1.1.0"
    },
    "ruleTemplate": {
      "id": "ARCADIA_MYSTERY_RULES",
      "version": "1.1.0"
    },
    "blueprintSha256": "64자리 SHA-256",
    "generationAttemptCount": 1,
    "generationSource": "AI",
    "model": "환경 변수로 선택된 모델",
    "promptVersion": "case-generator-v3",
    "createdAt": "2026-07-28T00:00:00Z",
    "frozenAt": "2026-07-28T00:00:08Z",
    "caseBlueprint": {
      "blueprintId": "CASE-GAME01K1ARCADIA9J2",
      "seed": "optional-replayable-seed",
      "worldTemplate": {
        "id": "ARCADIA_WORLD",
        "version": "1.1.0"
      },
      "ruleTemplate": {
        "id": "ARCADIA_MYSTERY_RULES",
        "version": "1.1.0"
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
백엔드 구현에는 위의 축약 없는 실제 응답 파일을 기준으로 사용하세요.

`caseBlueprint`에는 다음이 모두 들어갑니다.

- 사건 제목, 브리핑, 전체 진실과 허구적 수법
- 시간순 타임라인과 용의자별 실제 알리바이
- 사실, 핵심 단서, 미끼 단서와 획득 조건
- RAG 검색용 확정 기록
- NPC별 지식·은폐·증거 제시 해금 규칙
- 범인과 `SETUP`/`TRIGGER`/`OPPORTUNITY`/`MOTIVE` 정답 단서 ID
- 비범인별 배제 근거

주요 중첩 필드:

| 경로 | 타입 | 설명 |
|---|---|---|
| `generation.worldTemplate` | `{id, version}` | 사용한 세계관 템플릿 |
| `generation.ruleTemplate` | `{id, version}` | 사용한 추리 규칙 템플릿 |
| `generation.generationSource` | `AI \| FALLBACK` | 사건 생성 출처 |
| `generation.caseBlueprint.method` | object | 허구적 수법과 준비·촉발 액션 |
| `caseBlueprint.timeline[]` | array | 시각·행위자·장소·연결 사실 |
| `caseBlueprint.facts[]` | array | 서버가 확정한 참·거짓 사실 |
| `caseBlueprint.alibis[]` | array | 최초 주장과 실제 동선 |
| `caseBlueprint.clues[]` | array | 단서 유형·역할·출처·획득 조건 |
| `caseBlueprint.evidenceRecords[]` | array | RAG 검색용 확정 기록 |
| `caseBlueprint.npcKnowledge[]` | array | NPC 지식·은폐·해금 정책 |
| `caseBlueprint.redHerrings[]` | array | 미끼 단서와 해소 근거 |
| `caseBlueprint.solution` | object | 범인·역할별 정답 단서·비범인 배제 |

`generation`은 `READY`일 때 object이고 생성 중이나 실패 상태에서는 `null`입니다.
`errorCode`는 정상 상태에서 `null`, `FAILED`에서 string입니다. 배열 필드는 항목이
없어도 `null`이 아니라 빈 배열 `[]`로 응답합니다.

`generationSource`가 `FALLBACK`이어도 사건은 모든 검증을 통과한 상태이며 `status`는
`FAILED`가 아니라 `READY`입니다. 따라서 백엔드는 정상 게임을 시작하면 됩니다.

### 2.1 NPC 심문 대상과 characterId 일관성

백엔드는 `caseBlueprint.alibis[].characterId` 전체를 심문 가능한 용의자로 노출할 수
있습니다. AI 서버는 다음 규칙을 사건 생성 후 의미 검증기에서 강제합니다.

- `alibis`에 등장하는 모든 `characterId`에는 `npcKnowledge`가 존재합니다.
- 각 `npcKnowledge.initialClaimFactIds`는 비어 있지 않고 해당 인물의
  `alibis.supportingFactIds` 또는 `alibis.contradictingFactIds`에 연결됩니다.
- 각 `npcKnowledge.recommendedQuestionTopics`는 비어 있지 않습니다.
- 공개할 결정적 사실이 없는 인물은 `revealPolicies: []`일 수 있습니다.
- `culpritId`, `alibis`, `npcKnowledge`, `solution.nonCulpritExclusions`,
  `clues[].suspectEffects[]`의 인물 참조는 `worldTemplate.characters[].id`를
  대소문자까지 동일하게 사용합니다.
- `solution.nonCulpritExclusions`는 `alibis`의 인물 중 범인을 제외한 전원을 정확히
  한 번씩 포함합니다.

누락, 중복, 대소문자 차이 또는 잘못된 인물 참조가 있으면 사건을 동결하지 않고
재생성하며, 재시도 후에도 실패하면 검증 완료 fallback 사건으로 전환합니다.

### 2.2 탐사 장소 정식 로스터와 locationId 일관성

`ARCADIA_WORLD:1.1.0`의 탐사 장소는 다음 8개로 고정됩니다. 배열 순서도 아래와
같습니다.

| 순서 | locationId | 표시 이름 | 주요 구성 |
|---:|---|---|---|
| 1 | `COMMANDER_OFFICE` | 사령관실 | 범행 현장, 출입 기록 |
| 2 | `DEPUTY_COMMANDER_OFFICE` | 부사령관 집무실 | 헨드릭스 전용, 사령관실 직통 |
| 3 | `CENTRAL_HUB` | 중앙 허브 | 복도, 환경 제어 패널 |
| 4 | `MEDICAL_BAY` | 의무실 | 소피아 업무 구역 |
| 5 | `ENGINEERING_BAY` | 엔지니어링 | 백준호 업무 구역 |
| 6 | `COMMUNICATIONS_CENTER` | 통신실 | 카심 업무 구역 |
| 7 | `CARGO_BAY` | 화물칸 | 유나 업무 구역, 에어록 |
| 8 | `COMMON_AREA` | 공용구역 | 식당, 숙소 |

다음 필드의 비어 있지 않은 모든 `locationId`는 위 로스터 안의 값을 대소문자까지
동일하게 사용해야 합니다.

- `method.setupAction.locationId`
- `method.triggerAction.locationId`
- `timeline[].locationId`
- `clues[].acquisition.locationId`
- `evidenceRecords[].metadata.locationId`
- `POST /api/v1/sessions/{id}/explore` 요청의 `locationId`

월드 템플릿 자체가 정확히 이 8개를 포함하지 않으면 `LOCATION_ROSTER_MISMATCH`,
생성 사건이 로스터 밖의 장소를 참조하면 `NON_ROSTER_LOCATION_ID`로 거부됩니다.
탐사 API도 로스터 밖 ID에 `400 INVALID_REQUEST`를 반환합니다. 로스터 안의 8개
장소는 그 시점에 발견할 단서가 없어도 모두 정상 탐사할 수 있습니다.

### 2.3 오브젝트 상호작용 단서 커버리지

프론트 연동 계약은 `1.2.0`이며, 현재 화면에 있는 16개 조사 오브젝트를 모두
`EXPLORE` 대상으로 등록합니다. 그중 다음 10개는 `clueRequired: true`이고 8개 장소
전체를 적어도 한 번씩 커버합니다.

| objectHint | locationId |
|---|---|
| `CO_BODY` | `COMMANDER_OFFICE` |
| `CO_DOOR_LOG` | `COMMANDER_OFFICE` |
| `CO_XO_PASSAGE` | `DEPUTY_COMMANDER_OFFICE` |
| `CO_ENV_PANEL` | `CENTRAL_HUB` |
| `CO_TERMINAL` | `COMMANDER_OFFICE` |
| `MD_MEDICAL_STORAGE` | `MEDICAL_BAY` |
| `EN_LIFE_SUPPORT` | `ENGINEERING_BAY` |
| `CM_SECURITY_ARCHIVE` | `COMMUNICATIONS_CENTER` |
| `CG_CARGO_MANIFEST` | `CARGO_BAY` |
| `CMN_FOOD_STATION` | `COMMON_AREA` |

나머지 정식 오브젝트 ID는 `CO_SCANNER`, `HB_MAINTENANCE`, `XO_RESOURCE_BOARD`,
`MD_MEDICAL_TERMINAL`, `CG_AIRLOCK_LOG`, `QT_ACCESS_BUFFER`입니다. AI 생성 사건의
모든 `EXPLORE` 단서는 다음 조건을 만족해야 합니다.

- `source.sourceType`은 `PHYSICAL_OBJECT`입니다.
- `source.sourceId`는 위 16개 정식 오브젝트 ID 중 하나입니다.
- `acquisition.locationId`는 해당 오브젝트에 등록된 장소와 정확히 일치합니다.
- `clueRequired: true`인 10개 오브젝트에는 각각 하나 이상의 `EXPLORE` 단서가
  존재해야 합니다.

`ARCADIA_MYSTERY_RULES:1.1.0`에서 허용하는 단서 획득 방식은 `EXPLORE`,
`RAG_QUERY`, `CONNECT`입니다. 검증 완료 fallback 사건은 단서 14건으로 구성되며,
이 중 10건이 위 필수 오브젝트에서 획득하는 `EXPLORE` 단서이고 4건은
`RAG_QUERY` 단서입니다.

오브젝트 단위 탐사는 다음처럼 `objectHint`를 선택적으로 받습니다.

```http
POST /api/v1/sessions/{sessionId}/explore
Content-Type: application/json

{
  "locationId": "MEDICAL_BAY",
  "objectHint": "MD_MEDICAL_STORAGE"
}
```

- `objectHint`를 생략하면 기존 호환 동작으로 해당 `locationId`의 발견 가능한 단서를
  장소 단위로 반환합니다.
- `objectHint`를 제공하면 AI 서버는 ID가 16개 정식 로스터에 있는지, 오브젝트의 등록
  장소가 요청 `locationId`와 같은지 검증합니다. 성공하면 동결된 CaseBlueprint에서
  `clue.source.sourceId == objectHint`인 단서만 반환합니다.
- 알 수 없는 오브젝트 또는 장소가 맞지 않는 조합은 `400 INVALID_REQUEST`입니다.

프론트와 AI 서버가 별도 프로세스인 실제 배포에서는 **게임 백엔드가 프론트 요청의
`objectHint`를 AI 탐사 요청에 그대로 전달해야 합니다.** AI 탐사 API를 호출하지 않고
백엔드가 동결된 CaseBlueprint를 직접 해금하는 구조라면 동일하게
`clue.source.sourceId == objectHint`로 필터해야 합니다. 이를 누락하면 같은 방의 첫
오브젝트를 조사했을 때 그 방의 여러 오브젝트 단서가 한꺼번에 공개됩니다.

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
      "id": "CENTRAL_HUB",
      "displayName": "중앙 허브",
      "publicDescription": "모든 주요 방을 연결하며 환경 제어 패널이 설치된 중앙 복도다."
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

## 4. NPC/RAG 내부 호출 계약

사건 생성 요청에 사용한 `sessionId`를 NPC와 RAG에도 동일하게 사용합니다. 두 요청에도
사건 생성·조회와 같은 내부 키를 보냅니다.

```http
POST /api/v1/sessions/{sessionId}/interrogations/{characterId}/turns
X-Internal-AI-Key: <AI_INTERNAL_API_KEY>
Content-Type: application/json

{
  "question": "제시한 두 기록을 설명해 주세요.",
  "presentedClueIds": ["CLUE-TRIGGER-LOG", "CLUE-SETUP-PANEL"]
}
```

`presentedClueIds`의 발견 여부에 대한 기준 데이터는 백엔드의 `EvidenceInventory`입니다.
백엔드는 미발견 단서를 제거하고, 해당 NPC에게 지금까지 제시한 발견 단서의 누적 목록을
보냅니다. AI 서버는 각 ID가 동결된 CaseBlueprint에 존재하는지만 검사합니다.

이 계약에 따라 백엔드가 단독으로 해금한 `EXPLORE`/`CONNECT` 단서도 별도 동기화 API
없이 NPC에게 제시할 수 있습니다. CaseBlueprint에 없는 단서 ID는
`400 INVALID_REQUEST`입니다.

```http
POST /api/v1/sessions/{sessionId}/assistant/queries
X-Internal-AI-Key: <AI_INTERNAL_API_KEY>
Content-Type: application/json

{
  "question": "02:05 안전 진단 기록을 보여 주세요."
}
```

`AI_INTERNAL_API_KEY`가 설정된 환경에서 사건 생성·조회, NPC, RAG 요청의 헤더가 없거나
값이 다르면 다음 오류를 반환합니다.

```json
{
  "timestamp": "2026-07-30T00:00:00Z",
  "status": 403,
  "code": "INVALID_INTERNAL_API_KEY",
  "message": "Invalid internal API key"
}
```

## 5. 오류 처리

- `400`: 요청 형식 또는 ID가 잘못됨
- `403`: 내부 API 키 불일치
- `404`: 세션 없음
- `409`: 중복 `sessionId` 또는 잘못된 상태
- `FAILED`: 내장 폴백까지 로드하지 못한 서버 구성 오류

네트워크 타임아웃이나 모델 거부, 구조화 출력 실패, 논리 검증 실패는 최대 두 번
재시도한 뒤 검증 완료 폴백으로 전환하므로 일반적으로 `READY`로 끝납니다.
