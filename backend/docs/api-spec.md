# 아르카디아 스테이션 백엔드 API 명세서

> 대상: 프론트엔드(React) 개발자
> Base URL: 로컬 개발 `http://localhost:8080`, 배포 URL은 별도 공유
> 전체 게임 흐름은 [`docs/gameplay-flow.md`](./gameplay-flow.md) 참고

## 변경 이력

### 2026-08-06 — 단서 문맥·배제 판정·오답 종료 확장

`docs/FRONTEND_BACKEND_CLUE_CONTEXT_REQUEST_2026-08-06.md` 요청 A/B/C를 순서대로 반영했습니다. 기존 필드는 전부 유지되는 순수 추가 변경이라 하위 호환됩니다.

- **요청 A (0.3절 표, 3·4·5번 API):** 단서 응답(`discoveredClues`/탐사 응답/RAG `newlyDiscoveredClues`)에 `isCore`, `revealedFacts[]`, `linkedClueIds`, `suspectEffects[]`, `hasPendingConnection` 5개 필드 추가. `revealedFacts`엔 정답에 해당하는 `truthValue`를 담지 않고, `linkedClueIds`엔 미발견 단서 ID가 섞이지 않으며, `hasPendingConnection`은 대상 단서 ID 없이 boolean만 내려가는 등 스포일러 경계를 유지합니다. 최종 판정에 쓰이는 `solutionRoles`는 여전히 노출하지 않습니다.
- **요청 B (7번 API):** 추리 제출 요청에 `exclusionsByCharacter`(범인이 아닌 용의자에 대한 배제 근거) 추가, 응답에 `exclusionResults`와 구조화된 `missingLogic[]` 추가. 생략 시 기존과 동일하게 동작합니다.
- **요청 C (0.3절 표, 7·8번 API):** 세션 상태에 `INCORRECT`(오답 소진 종료) 신설. 오답 제출이 최대 횟수(기본 3회)에 도달하면 세션이 자동으로 `INCORRECT`로 전이되고, `GET /result`가 `COMPLETED`뿐 아니라 `INCORRECT`에서도 200을 반환하도록 완화했습니다. 이에 따라 7번 API의 409 사유가 "오답 횟수 초과"에서 "이미 종료된 세션에 제출 시도"로 바뀌었고, 내부적으로만 쓰이던 `DEDUCTION_LIMIT_EXCEEDED` 에러 코드는 제거됐습니다(원래 API 응답 바디에 노출되던 코드가 아니라 프론트 영향 없음).
- 심문 응답(NPC 턴) 확장은 이번 변경에 포함되지 않았습니다 — 요청 문서 6절에 따라 별도 설계 합의 후 진행 예정입니다.

## 0. 공통 사항 (모든 API 공통, 읽고 시작하세요)

### 0.1 응답 공통 포맷

모든 응답은 아래 형태로 감싸져서 옵니다. `data`의 내용만 API마다 다릅니다.

```jsx
{
  "success": true,
  "message": null,
  "data": { /* API마다 다른 내용 */ }
}
```

| key | 설명 | value 타입 | Nullable |
| --- | --- | --- | --- |
| success | 성공 여부 | boolean | N |
| message | 실패 시 에러 메시지(성공 시 null) | String | Y |
| data | 실제 응답 데이터 | Object / Array | Y (에러 시 null) |

### 0.2 에러 공통 포맷

에러가 나면 `success: false`, `data: null`이고 `message`에 사람이 읽을 문구가 담깁니다. **현재는 프론트에서 분기할 수 있는 별도의 에러 코드 문자열(`code`)이 없습니다.** HTTP status로만 구분해야 합니다.

```jsx
{
  "success": false,
  "message": "세션을 찾을 수 없습니다.",
  "data": null
}
```

| HTTP status | 의미 | 발생하는 API |
| --- | --- | --- |
| 400 | 요청이 올바르지 않음(빈 질문, 미발견 단서 제시, 존재하지 않는 인물 등) | 심문, RAG 검색, 추리 제출 |
| 404 | 세션을 찾을 수 없음(잘못된 sessionId) | 전체 API 공통 |
| 409 | 지금은 처리할 수 없는 세션 상태(사건 아직 생성 중, 정답/오답으로 이미 종료됨 등) | 심문, RAG 검색, 추리 제출, 결과 조회 |

### 0.3 세션 상태값(`status`)

여러 API 응답에 공통으로 등장하는 값입니다.

| 값 | 의미 | 프론트 화면 |
| --- | --- | --- |
| CREATING | 사건 생성 요청 전송 완료, 생성 중 | 로딩 화면 |
| VALIDATING | AI 서버가 검증 중 | 로딩 화면 |
| READY | 사건 생성 완료(내부 상태, 프론트가 보기 전) | 로딩 화면 |
| BRIEFING | 브리핑 노출 가능 | 브리핑 화면 |
| INVESTIGATION | 탐사/심문/검색 진행 중 | 조사 화면 |
| DEDUCTION | 최종 추리 제출 단계 | 추리 제출 화면 |
| COMPLETED | 정답으로 판정 완료 | 결과 화면 |
| INCORRECT | 오답을 최대 횟수(기본 3회)만큼 소진해 종료됨 | 결과 화면(오답 엔딩) |
| FAILED | 사건 생성 자체가 실패함 | 에러 화면(재시도 유도) |

### 0.4 sessionId는 어디서 얻나요

1번 API(`POST /api/v1/sessions`) 응답의 `data.sessionId`가 이후 모든 API 경로의 `{id}`입니다. 로컬 스토리지 등에 저장해두고 계속 사용하세요.

---

## 1. 세션 생성

새 게임을 시작합니다. 사건 생성이 백그라운드에서 진행되니, 이 API는 즉시 응답하고 프론트는 2번 API로 폴링해야 합니다.

### Request

**Request Body**

| key | 설명 | value 타입 | 필수 | 예시 |
| --- | --- | --- | --- | --- |
| seed | 리플레이용 시드(같은 사건을 재현하고 싶을 때만 사용) | String | N | null |

**Request Example**

```
POST /api/v1/sessions
Content-Type: application/json
```

```jsx
{ "seed": null }
```

바디 자체를 생략해도 됩니다(`{}` 또는 빈 바디).

### Response

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data.sessionId | 이후 모든 API에서 사용할 세션 ID | String | N | "game_ec3c9086655e4179842cc9e851673a7f" |
| data.status | 세션 상태(0.3절 참고) | String | N | "CREATING" |

**Example**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "sessionId": "game_ec3c9086655e4179842cc9e851673a7f",
    "status": "CREATING"
  }
}
```

> **구현 참고:** 로컬 개발 환경(Fake AI)에서는 사건 생성이 즉시 끝나서 `status`가 바로 `"BRIEFING"`으로 올 수도 있습니다. 실제 배포 환경(진짜 AI 서버 연동)에서는 사건 생성에 수십 초가 걸릴 수 있어 `"CREATING"`으로 응답하고, 프론트가 2번 API로 폴링하면서 기다려야 합니다. **`status` 값에 따라 분기하되 값 자체를 하드코딩해서 "무조건 CREATING이 온다"고 가정하지 마세요.**

### Status

| status | response content |
| --- | --- |
| 202 | 세션 생성 요청 접수 |

---

## 2. 세션 상태 조회 (폴링용)

브리핑이 준비됐는지 확인하기 위해 주기적으로 호출하는 API입니다. 로딩 화면에서 2~3초 간격으로 폴링하세요.

### Request

**Path variable**

| key | 설명 | value 타입 | 예시 |
| --- | --- | --- | --- |
| id | 세션 ID | String | game_ec3c9086655e4179842cc9e851673a7f |

**Request Example**

```
GET /api/v1/sessions/game_ec3c9086655e4179842cc9e851673a7f/status
```

### Response

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data.sessionId | 세션 ID | String | N | "game_ec3c9086655e4179842cc9e851673a7f" |
| data.status | 세션 상태(0.3절 참고) | String | N | "BRIEFING" |

**Example**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "sessionId": "game_ec3c9086655e4179842cc9e851673a7f",
    "status": "BRIEFING"
  }
}
```

### Status

| status | response content |
| --- | --- |
| 200 | 조회 성공 |
| 404 | 존재하지 않는 sessionId |

**프론트 구현 팁:** `status`가 `"BRIEFING"`이나 `"FAILED"`가 될 때까지 폴링을 반복하고, `"BRIEFING"`이면 3번 API(브리핑 조회)로 넘어가세요. `"FAILED"`면 폴링을 멈추고 에러 화면(재시도 버튼 등)을 보여주세요.

---

## 3. 플레이어 공개 상태 조회 (브리핑 · 발견 단서 목록)

사건 개요와 지금까지 발견한 단서 목록을 보여줍니다. 조사 화면에 진입할 때마다, 그리고 탐사/검색으로 새 단서를 얻은 뒤 최신 상태를 다시 확인할 때 호출하세요.

### Request

**Path variable**

| key | 설명 | value 타입 | 예시 |
| --- | --- | --- | --- |
| id | 세션 ID | String | game_ec3c9086655e4179842cc9e851673a7f |

**Request Example**

```
GET /api/v1/sessions/game_ec3c9086655e4179842cc9e851673a7f
```

### Response

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data.sessionId | 세션 ID | String | N | "game_ec3c9086655e4179842cc9e851673a7f" |
| data.status | 세션 상태(0.3절 참고) | String | N | "INVESTIGATION" |
| data.title | 사건 제목 | String | Y (사건 생성 전이면 null) | "아르카디아 스테이션 사건" |
| data.briefing | 사건 개요/브리핑 문구 | String | Y | "생명 유지 시스템이 갑작스레 정지했다..." |
| data.discoveredClues[].clueId | 단서 ID | String | N | "CLUE-SETUP-LOG" |
| data.discoveredClues[].title | 단서 제목 | String | N | "의료 안전 점검 예약 기록" |
| data.discoveredClues[].clueType | 단서 종류 (`PHYSICAL`/`DIGITAL`/`MOTIVE`/`OPPORTUNITY`) | String | N | "DIGITAL" |
| data.discoveredClues[].playerText | 단서 발견 시 노출 문구 | String | N | "의료 베이 단말기에 소피아 명의로..." |
| data.discoveredClues[].isCore | 핵심 단서 여부 | boolean | N | true |
| data.discoveredClues[].revealedFacts[].factId | 이 단서가 드러내는 사실 ID | String | N | "FACT-SETUP" |
| data.discoveredClues[].revealedFacts[].statement | 사실 내용(참/거짓 여부는 내려주지 않음) | String | N | "소피아가 의료 안전 점검을 예약했다." |
| data.discoveredClues[].linkedClueIds | 같은 사실을 공유하는 **이미 발견한** 다른 단서 ID 목록(미발견 단서는 절대 섞이지 않음) | String[] | N (빈 배열 가능) | ["CLUE-ACCESS-HISTORY"] |
| data.discoveredClues[].suspectEffects[].characterId | 이 단서가 영향을 주는 인물 | String | N | "SOPHIA" |
| data.discoveredClues[].suspectEffects[].effect | 그 인물에 대한 효과 (`SUPPORTS`/`EXCLUDES`/`NEUTRAL`) | String | N | "SUPPORTS" |
| data.discoveredClues[].hasPendingConnection | 아직 발견하지 못한 CONNECT형 단서 중 이 단서가 재료가 되는 게 있는지(대상 ID는 노출하지 않음) | boolean | N | true |
| data.suspectCharacterIds | 이 사건에 등장하는 용의자 전원의 ID 목록(단서 발견 여부와 무관하게 처음부터 전부 노출). 6번 API(NPC 심문)의 `{characterId}` 경로 값은 **이 배열 안에서만** 골라 써야 함 | String[] | N | ["SOPHIA", "MAYA", "JUNHO", "KASIM", "YUNA"] |
| data.exploreLocationIds | 탐사 가능한 장소 ID 목록(단서 발견 여부와 무관하게 처음부터 전부 노출). 4번 API(장소 탐사)의 `locationId`는 **이 배열 안에서만** 골라 써야 함. `ARCADIA_WORLD:1.1.0` 정식 로스터 8개로 고정이며 모든 사건에서 동일함(그래도 프론트는 이 배열을 통해 받아 쓰고 하드코딩은 하지 않는 걸 권장) | String[] | N | ["COMMANDER_OFFICE", "DEPUTY_COMMANDER_OFFICE", "CENTRAL_HUB", "MEDICAL_BAY", "ENGINEERING_BAY", "COMMUNICATIONS_CENTER", "CARGO_BAY", "COMMON_AREA"] |

**Example**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "sessionId": "game_ec3c9086655e4179842cc9e851673a7f",
    "status": "INVESTIGATION",
    "title": "아르카디아 스테이션 사건",
    "briefing": "생명 유지 시스템이 갑작스레 정지했다. 승무원 소피아를 포함한 용의자들을 조사하라.",
    "discoveredClues": [
      {
        "clueId": "CLUE-SETUP-LOG",
        "title": "의료 안전 점검 예약 기록",
        "clueType": "DIGITAL",
        "playerText": "의료 베이 단말기에 소피아 명의로 안전 점검 예약 기록이 남아있다.",
        "isCore": true,
        "revealedFacts": [
          { "factId": "FACT-SETUP", "statement": "소피아가 의료 안전 점검을 예약했다." }
        ],
        "linkedClueIds": [],
        "suspectEffects": [
          { "characterId": "SOPHIA", "effect": "SUPPORTS" }
        ],
        "hasPendingConnection": true
      }
    ],
    "suspectCharacterIds": ["SOPHIA", "MAYA", "JUNHO", "KASIM", "YUNA"],
    "exploreLocationIds": ["COMMANDER_OFFICE", "DEPUTY_COMMANDER_OFFICE", "CENTRAL_HUB", "MEDICAL_BAY", "ENGINEERING_BAY", "COMMUNICATIONS_CENTER", "CARGO_BAY", "COMMON_AREA"]
  }
}
```

> **보안 참고:** 이 응답에는 범인(`culpritId`), 진실 요약, 아직 발견하지 않은 단서, NPC의 숨긴 사실 같은 스포일러 필드가 **절대 포함되지 않습니다.** `suspectCharacterIds`/`exploreLocationIds`는 ID만 나열할 뿐 누가 범인인지, 어디에 무슨 단서가 있는지는 알려주지 않으므로 안전합니다. 프론트는 이 응답을 그대로 화면에 렌더링해도 안전합니다.
>
> `revealedFacts`에는 `truthValue`(참/거짓 여부)가 없고, `linkedClueIds`는 이미 발견한 단서로만 채워지며, `hasPendingConnection`은 대상 단서 ID 없이 boolean만 내려가 미발견 단서의 존재를 유출하지 않습니다. `suspectEffects`는 의도적으로 노출하는 필드입니다(`playerText`를 읽어도 어차피 드러나는 수준이라 스포일러 아님). 반대로 최종 판정에 쓰이는 `solutionRoles`(SETUP/TRIGGER/OPPORTUNITY/MOTIVE 태그)는 정답을 그대로 드러내므로 **의도적으로 내려주지 않습니다** — "이 증거를 어느 역할에 쓸지"는 프론트가 UI로 플레이어에게 직접 태깅하게 하세요.
>
> **⚠️ 알려진 제약:** 현재 6번 API(NPC 심문) 백엔드 검증은 AI 서버가 생성한 `npcKnowledge`에 있는 인물만 허용합니다. 사건에 따라 `npcKnowledge`가 `suspectCharacterIds`의 일부만 커버할 수 있어(예: 범인만), 그 경우 목록에 없는 인물을 심문 시도하면 400이 날 수 있습니다. 전 용의자 심문을 100% 보장하려면 AI 서버 쪽에서 전 용의자분 `npcKnowledge` 생성이 필요하며, 별도로 진행 중입니다.

### Status

| status | response content |
| --- | --- |
| 200 | 조회 성공 |
| 404 | 존재하지 않는 sessionId |

---

## 4. 장소 탐사

플레이어가 특정 장소를 조사할 때 호출합니다. 조건을 만족하는 단서가 있으면 해금되고, 없으면 빈 배열이 옵니다(에러 아님).

### Request

**Path variable**

| key | 설명 | value 타입 | 예시 |
| --- | --- | --- | --- |
| id | 세션 ID | String | game_ec3c9086655e4179842cc9e851673a7f |

**Request Body**

| key | 설명 | value 타입 | 필수 | 예시 |
| --- | --- | --- | --- | --- |
| locationId | 조사할 장소 ID. 3번 API 응답의 `exploreLocationIds` 목록(정식 로스터 8개, 모든 사건에서 동일)에서만 골라야 함 | String | Y | "MEDICAL_BAY" |
| objectHint | 오브젝트 힌트(선택). 주어지면 해당 장소의 단서 중 `source.sourceId`가 이 값과 일치하는 단서만 해금 대상으로 필터링함. 생략하면 기존처럼 해당 장소의 해금 가능한 단서를 모두 반환(하위 호환). 장소와 맞지 않는 오브젝트를 보내도 에러 없이 빈 배열을 반환함 | String | N | null |

**Request Example**

```
POST /api/v1/sessions/game_ec3c9086655e4179842cc9e851673a7f/explore
Content-Type: application/json
```

```jsx
{ "locationId": "MEDICAL_BAY" }
```

### Response

`data`는 **이번 탐사로 새로 해금된 단서의 배열**입니다. 이미 발견한 단서이거나 조건 미충족이면 빈 배열입니다.

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data[].clueId | 단서 ID | String | N | "CLUE-SETUP-LOG" |
| data[].title | 단서 제목 | String | N | "의료 안전 점검 예약 기록" |
| data[].clueType | 단서 종류 | String | N | "DIGITAL" |
| data[].playerText | 발견 시 노출 문구 | String | N | "의료 베이 단말기에 소피아 명의로..." |
| data[].isCore | 핵심 단서 여부 | boolean | N | true |
| data[].revealedFacts[].factId | 이 단서가 드러내는 사실 ID | String | N | "FACT-SETUP" |
| data[].revealedFacts[].statement | 사실 내용 | String | N | "소피아가 의료 안전 점검을 예약했다." |
| data[].linkedClueIds | 같은 사실을 공유하는 이미 발견한 단서 ID 목록(3번 API와 동일 규칙) | String[] | N (빈 배열 가능) | [] |
| data[].suspectEffects[].characterId | 영향받는 인물 | String | N | "SOPHIA" |
| data[].suspectEffects[].effect | 효과 (`SUPPORTS`/`EXCLUDES`/`NEUTRAL`) | String | N | "SUPPORTS" |
| data[].hasPendingConnection | 아직 발견하지 못한 CONNECT형 단서의 재료인지(3번 API와 동일 규칙) | boolean | N | true |

**Example (단서를 찾은 경우)**

```jsx
{
  "success": true,
  "message": null,
  "data": [
    {
      "clueId": "CLUE-SETUP-LOG",
      "title": "의료 안전 점검 예약 기록",
      "clueType": "DIGITAL",
      "playerText": "의료 베이 단말기에 소피아 명의로 안전 점검 예약 기록이 남아있다.",
      "isCore": true,
      "revealedFacts": [
        { "factId": "FACT-SETUP", "statement": "소피아가 의료 안전 점검을 예약했다." }
      ],
      "linkedClueIds": [],
      "suspectEffects": [
        { "characterId": "SOPHIA", "effect": "SUPPORTS" }
      ],
      "hasPendingConnection": true
    }
  ]
}
```

**Example (해당 장소에 지금 해금할 단서가 없는 경우 — 정상)**

```jsx
{ "success": true, "message": null, "data": [] }
```

### Status

| status | response content |
| --- | --- |
| 200 | 처리 완료(단서 유무와 무관하게 200) |
| 404 | 존재하지 않는 sessionId |
| 409 | 아직 조사를 시작할 수 없는 세션 상태(브리핑 전 등) |

**프론트 구현 팁:** 응답 배열이 비어있어도 에러가 아니므로 "여기서는 특별한 게 없다" 같은 문구로 자연스럽게 처리하세요. 새 단서를 받으면 3번 API(`GET /api/v1/sessions/{id}`)로 전체 단서 목록을 다시 조회해서 목록 화면을 갱신하는 걸 권장합니다.

---

## 5. 사건기록 검색 (RAG)

플레이어가 자연어로 질문을 입력해 사건 기록을 검색합니다. 검색 결과로 단서가 직접 해금될 수도 있습니다.

### Request

**Path variable**

| key | 설명 | value 타입 | 예시 |
| --- | --- | --- | --- |
| id | 세션 ID | String | game_ec3c9086655e4179842cc9e851673a7f |

**Request Body**

| key | 설명 | value 타입 | 필수 | 예시 |
| --- | --- | --- | --- | --- |
| question | 플레이어가 입력한 검색 질문(공백 불가) | String | Y | "02:05 안전 진단 기록을 보여줘" |

**Request Example**

```
POST /api/v1/sessions/game_ec3c9086655e4179842cc9e851673a7f/assistant/queries
Content-Type: application/json
```

```jsx
{ "question": "02:05 안전 진단 기록을 보여줘" }
```

### Response

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data.answer | 검색된 기록 기반 요약 답변 | String | N | "02:05 생명 유지 시스템에서..." |
| data.citedRecordIds | 답변의 근거가 된 기록 ID 목록 | String[] | N (빈 배열 가능) | ["RECORD-TRIGGER"] |
| data.suggestedQueries | 후속 검색어 추천 (보통 2개) | String[] | N | ["다른 시각의 기록도 보여줘", "다른 인물 관련 기록을 보여줘"] |
| data.newlyDiscoveredClues[].clueId | 이번 검색으로 새로 해금된 단서 ID | String | N | "CLUE-TRIGGER-LOG" |
| data.newlyDiscoveredClues[].title | 단서 제목 | String | N | "02:05 안전 진단 감사 로그" |
| data.newlyDiscoveredClues[].clueType | 단서 종류 | String | N | "DIGITAL" |
| data.newlyDiscoveredClues[].playerText | 발견 시 노출 문구 | String | N | "02:05에 소피아의 인증으로..." |
| data.newlyDiscoveredClues[].isCore | 핵심 단서 여부 | boolean | N | true |
| data.newlyDiscoveredClues[].revealedFacts[].factId | 이 단서가 드러내는 사실 ID | String | N | "FACT-TRIGGER" |
| data.newlyDiscoveredClues[].revealedFacts[].statement | 사실 내용 | String | N | "02:05 소피아 인증으로 안전 진단이 실행됐다." |
| data.newlyDiscoveredClues[].linkedClueIds | 같은 사실을 공유하는 이미 발견한 단서 ID 목록(3번 API와 동일 규칙) | String[] | N (빈 배열 가능) | [] |
| data.newlyDiscoveredClues[].suspectEffects[].characterId | 영향받는 인물 | String | N | "SOPHIA" |
| data.newlyDiscoveredClues[].suspectEffects[].effect | 효과 (`SUPPORTS`/`EXCLUDES`/`NEUTRAL`) | String | N | "SUPPORTS" |
| data.newlyDiscoveredClues[].hasPendingConnection | 아직 발견하지 못한 CONNECT형 단서의 재료인지(3번 API와 동일 규칙) | boolean | N | false |

**Example**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "answer": "02:05 생명 유지 시스템에서 의료 안전 진단이 실행됐다.",
    "citedRecordIds": ["RECORD-TRIGGER"],
    "suggestedQueries": ["다른 시각의 기록도 보여줘", "다른 인물 관련 기록을 보여줘"],
    "newlyDiscoveredClues": [
      {
        "clueId": "CLUE-TRIGGER-LOG",
        "title": "02:05 안전 진단 감사 로그",
        "clueType": "DIGITAL",
        "playerText": "02:05에 소피아의 인증으로 의료 안전 진단 작업이 실행됐다.",
        "isCore": true,
        "revealedFacts": [
          { "factId": "FACT-TRIGGER", "statement": "02:05 소피아 인증으로 안전 진단이 실행됐다." }
        ],
        "linkedClueIds": [],
        "suspectEffects": [
          { "characterId": "SOPHIA", "effect": "SUPPORTS" }
        ],
        "hasPendingConnection": false
      }
    ]
  }
}
```

관련 기록을 못 찾으면 `newlyDiscoveredClues`와 `citedRecordIds`가 빈 배열로 오고 `answer`에 "관련 기록을 찾지 못했습니다." 같은 문구가 옵니다(에러 아님, 200).

### Status

| status | response content |
| --- | --- |
| 200 | 처리 완료 |
| 400 | 빈 질문(공백만 입력) |
| 404 | 존재하지 않는 sessionId |
| 409 | 아직 검색할 수 없는 세션 상태 |

**프론트 구현 팁:** `question`은 프론트에서 미리 `trim()` 후 빈 문자열이면 아예 요청을 보내지 않도록 막아주면 400을 피할 수 있습니다. `suggestedQueries`는 "다음 질문 추천" 칩/버튼 UI로 쓰면 됩니다.

---

## 6. NPC 심문

용의자에게 질문하고 발견한 단서를 증거로 제시합니다. 제시한 단서에 대응하는 사실이 있으면 대사에 반영되어 공개됩니다.

### Request

**Path variable**

| key | 설명 | value 타입 | 예시 |
| --- | --- | --- | --- |
| id | 세션 ID | String | game_ec3c9086655e4179842cc9e851673a7f |
| characterId | 심문할 인물 ID. 3번 API 응답의 `suspectCharacterIds` 목록에서만 골라야 함(그 외 값은 400) | String | "SOPHIA" |

**Request Body**

| key | 설명 | value 타입 | 필수 | 예시 |
| --- | --- | --- | --- | --- |
| question | 플레이어 질문(공백 불가) | String | Y | "그 기록을 설명해 주세요." |
| presentedClueIds | 이번 턴에 제시하는 단서 ID 목록. **반드시 이미 발견한 단서만** 넣을 수 있습니다 | String[] | Y (빈 배열 가능) | ["CLUE-TRIGGER-LOG"] |

**Request Example**

```
POST /api/v1/sessions/game_ec3c9086655e4179842cc9e851673a7f/interrogations/SOPHIA/turns
Content-Type: application/json
```

```jsx
{
  "question": "그 기록을 설명해 주세요.",
  "presentedClueIds": ["CLUE-TRIGGER-LOG"]
}
```

### Response

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data.dialogue | NPC 대사 | String | N | "그 증거를 보여주신다면... 인정할 수밖에 없겠네요." |
| data.emotion | NPC 감정 상태 (`CALM`/`DEFENSIVE`/`ANXIOUS`/`ANGRY`/`EVASIVE`) | String | N | "DEFENSIVE" |
| data.revealedFactIds | 이번 턴에 공개된 사실 ID 목록 (내부 판정용, 화면에 ID 자체를 노출할 필요는 없음) | String[] | N (빈 배열 가능) | ["FACT-TRIGGER"] |
| data.recommendedQuestions[].topicId | 추천 질문 토픽 ID | String | N | "TOPIC-537062017" |
| data.recommendedQuestions[].label | 추천 질문 문구(버튼에 표시) | String | N | "안전 점검 예약의 목적" |

**Example**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "dialogue": "그 증거를 보여주신다면... 인정할 수밖에 없겠네요.",
    "emotion": "DEFENSIVE",
    "revealedFactIds": ["FACT-TRIGGER"],
    "recommendedQuestions": [
      { "topicId": "TOPIC-537062017", "label": "안전 점검 예약의 목적" },
      { "topicId": "TOPIC-1621918791", "label": "02:05 진단 실행 시각" }
    ]
  }
}
```

### Status

| status | response content |
| --- | --- |
| 200 | 처리 완료(AI 응답이 일시적으로 불가능한 경우도 200 + 고정 안전 문구로 옴, 아래 참고) |
| 400 | 빈 질문, 존재하지 않는 characterId, 아직 발견하지 않은 단서를 제시 |
| 404 | 존재하지 않는 sessionId |
| 409 | 아직 심문할 수 없는 세션 상태 |

**프론트 구현 팁:**
- `presentedClueIds`로 넣을 수 있는 단서 목록은 3번 API의 `discoveredClues` 목록에서만 골라 UI로 제공하세요(그 외 값은 400).
- 한 번 제시한 단서는 이후 턴에서 다시 넣지 않아도 백엔드가 알아서 누적 기억합니다. 매번 전체 다시 보낼 필요 없습니다.
- 이 API는 내부적으로 AI 서버와 통신합니다. AI 서버가 일시적으로 응답하지 못해도 **에러가 아니라 200 + "지금은 대답할 수 없습니다. 잠시 후 다시 시도해주세요." 같은 안전한 대사로 응답**하도록 백엔드가 처리해뒀습니다. 별도 에러 처리 없이 그냥 대사로 보여주면 됩니다.

---

## 7. 최종 추리 제출

수사를 마친 뒤 범인과 4가지 역할(SETUP/TRIGGER/OPPORTUNITY/MOTIVE)에 해당하는 증거를 제출합니다. LLM을 거치지 않는 결정적 판정입니다.

### Request

**Path variable**

| key | 설명 | value 타입 | 예시 |
| --- | --- | --- | --- |
| id | 세션 ID | String | game_ec3c9086655e4179842cc9e851673a7f |

**Request Body**

| key | 설명 | value 타입 | 필수 | 예시 |
| --- | --- | --- | --- | --- |
| culpritId | 지목할 범인 ID | String | Y | "SOPHIA" |
| evidenceByRole.SETUP | 준비 단계 증거 단서 ID | String | Y | "CLUE-SETUP-LOG" |
| evidenceByRole.TRIGGER | 실행 트리거 증거 단서 ID | String | Y | "CLUE-TRIGGER-LOG" |
| evidenceByRole.OPPORTUNITY | 기회/권한 증거 단서 ID | String | Y | "CLUE-ACCESS-HISTORY" |
| evidenceByRole.MOTIVE | 동기 증거 단서 ID | String | Y | "CLUE-MOTIVE-MESSAGE" |
| exclusionsByCharacter.{characterId} | 범인으로 지목하지 않은 용의자를 배제하는 근거 단서 ID. 범인으로 지목한 사람은 키로 보낼 수 없음(400) | String | N (생략 시 하위 호환으로 기존과 동일 동작) | {"MARCUS": "CLUE-ACCESS-HISTORY"} |

**Request Example**

```
POST /api/v1/sessions/game_ec3c9086655e4179842cc9e851673a7f/deductions
Content-Type: application/json
```

```jsx
{
  "culpritId": "SOPHIA",
  "evidenceByRole": {
    "SETUP": "CLUE-SETUP-LOG",
    "TRIGGER": "CLUE-TRIGGER-LOG",
    "OPPORTUNITY": "CLUE-ACCESS-HISTORY",
    "MOTIVE": "CLUE-MOTIVE-MESSAGE"
  },
  "exclusionsByCharacter": {
    "MARCUS": "CLUE-ACCESS-HISTORY"
  }
}
```

> **주의:** 4개 역할(SETUP/TRIGGER/OPPORTUNITY/MOTIVE)을 **전부** 채워야 합니다. 하나라도 빠지면 400입니다. `exclusionsByCharacter`의 단서도 `evidenceByRole`과 동일하게 **이미 발견한 단서만** 허용되고(그 외 400), 같은 단서를 여러 명의 배제에 재사용할 수 있습니다.

### Response

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data.verdict | 최종 판정 (`CORRECT`/`PARTIAL`/`INCORRECT`) | String | N | "PARTIAL" |
| data.culpritCorrect | 범인 지목이 맞았는지 | boolean | N | true |
| data.roleResults.SETUP | SETUP 증거 정오 (`CORRECT`/`INCORRECT`) | String | N | "CORRECT" |
| data.roleResults.TRIGGER | TRIGGER 증거 정오 | String | N | "CORRECT" |
| data.roleResults.OPPORTUNITY | OPPORTUNITY 증거 정오 | String | N | "INCORRECT" |
| data.roleResults.MOTIVE | MOTIVE 증거 정오 | String | N | "CORRECT" |
| data.remainingAttempts | 남은 제출 가능 횟수(기본 3회에서 오답/부분정답마다 차감, 정답이면 안 깎임) | int | N | 2 |
| data.feedback | 결과 안내 문구(정답 단서 ID 등 스포일러는 절대 없음) | String | N | "범인은 맞지만 기회와 권한 증거를 다시 확인해야 합니다." |
| data.exclusionResults.{characterId} | 요청에 보낸 배제 근거의 정오 (`CORRECT`/`INSUFFICIENT`). `exclusionsByCharacter`를 안 보냈으면 빈 객체 | Object&lt;String, String&gt; | N | {"MARCUS": "CORRECT"} |
| data.missingLogic[].code | 부족한 논리 종류 (`WRONG_CULPRIT`/`WEAK_ROLE_EVIDENCE`/`WEAK_EXCLUSION`) | String | N | "WEAK_EXCLUSION" |
| data.missingLogic[].role | `WEAK_ROLE_EVIDENCE`일 때만 채워지는 역할명 | String | Y | "OPPORTUNITY" |
| data.missingLogic[].characterId | `WEAK_EXCLUSION`일 때만 채워지는 인물 ID | String | Y | "MARCUS" |
| data.missingLogic[].message | 안내 문구(정답 단서 ID·정답 인물은 절대 포함 안 함) | String | N | "마커스를 배제할 근거가 부족합니다." |

**Example (완전 정답 — 게임 종료)**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "verdict": "CORRECT",
    "culpritCorrect": true,
    "roleResults": { "SETUP": "CORRECT", "TRIGGER": "CORRECT", "OPPORTUNITY": "CORRECT", "MOTIVE": "CORRECT" },
    "remainingAttempts": 3,
    "feedback": "정확한 추리입니다. 사건의 전모가 드러났습니다.",
    "exclusionResults": { "MARCUS": "CORRECT" },
    "missingLogic": []
  }
}
```

**Example (부분 정답 — 계속 진행 가능, 배제 근거도 부족한 경우)**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "verdict": "PARTIAL",
    "culpritCorrect": true,
    "roleResults": { "SETUP": "CORRECT", "TRIGGER": "CORRECT", "OPPORTUNITY": "INCORRECT", "MOTIVE": "CORRECT" },
    "remainingAttempts": 2,
    "feedback": "범인은 맞지만 기회와 권한 증거를 다시 확인해야 합니다.",
    "exclusionResults": { "MARCUS": "INSUFFICIENT" },
    "missingLogic": [
      { "code": "WEAK_ROLE_EVIDENCE", "role": "OPPORTUNITY", "characterId": null, "message": "기회와 권한 증거가 부족합니다." },
      { "code": "WEAK_EXCLUSION", "role": null, "characterId": "MARCUS", "message": "MARCUS를 배제할 근거가 부족합니다." }
    ]
  }
}
```

### Status

| status | response content |
| --- | --- |
| 200 | 판정 완료(정답/오답/부분정답 모두 200, `verdict` 값으로 구분) |
| 400 | 4개 역할 중 누락, 아직 발견하지 않은 단서 제출, 범인 본인을 `exclusionsByCharacter`에 포함 |
| 404 | 존재하지 않는 sessionId |
| 409 | 이미 종료된 세션(오답 소진으로 `INCORRECT` 전이됨, 또는 이미 `COMPLETED`)에 제출 시도 |

**프론트 구현 팁:** `verdict === "CORRECT"`이면 세션 상태가 자동으로 `COMPLETED`로, 오답을 3회(기본값) 소진하면 자동으로 `INCORRECT`로 바뀝니다. 두 경우 모두 바로 8번 API(`GET /result`)를 호출해서 결과 화면으로 넘어가세요. `remainingAttempts`가 0이 되면 세션이 이미 `INCORRECT`로 종료된 상태이니 제출 버튼을 비활성화하세요(그래도 시도하면 409).

---

## 8. 최종 사건 재구성 조회

정답을 맞혔거나(`COMPLETED`) 오답을 최대 횟수만큼 소진해 종료됐을 때(`INCORRECT`) 사건의 전체 진실을 공개합니다. 엔딩/크레딧 화면에서 사용하세요. 오답으로 끝났어도 해설을 볼 수 있습니다.

### Request

**Path variable**

| key | 설명 | value 타입 | 예시 |
| --- | --- | --- | --- |
| id | 세션 ID | String | game_ec3c9086655e4179842cc9e851673a7f |

**Request Example**

```
GET /api/v1/sessions/game_ec3c9086655e4179842cc9e851673a7f/result
```

### Response

| key | 설명 | value 타입 | Nullable | 예시 |
| --- | --- | --- | --- | --- |
| data.sessionId | 세션 ID | String | N | "game_ec3c9086655e4179842cc9e851673a7f" |
| data.culpritId | 실제 범인 | String | N | "SOPHIA" |
| data.truthSummary | 사건 진실 요약 | String | N | "소피아가 의료 안전 점검을 가장해..." |
| data.method.fictionalSummary | 수법 요약 | String | N | "안전 점검을 가장해 트리거를 심었다." |
| data.method.setupAction | 준비 행동 상세(아래 참고) | Object | N | — |
| data.method.triggerAction | 실행 행동 상세(아래 참고) | Object | N | — |
| data.method.victimCondition | 피해자 상태 조건 | String | N | "생명 유지 장치 의존 상태" |
| data.timeline[].eventId | 타임라인 이벤트 ID | String | N | "EVENT-TRIGGER" |
| data.timeline[].time | 발생 시각("HH:mm") | String | N | "02:05" |
| data.timeline[].actorIds | 관련 인물 ID 목록 | String[] | N | ["SOPHIA"] |
| data.timeline[].locationId | 발생 장소 | String | N | "LIFE_SUPPORT_CORRIDOR" |
| data.timeline[].actionType | 이벤트 종류 (`MOVEMENT`/`SYSTEM_ACTION`/`CONVERSATION`/`DISCOVERY`/`BACKGROUND`) | String | N | "SYSTEM_ACTION" |
| data.timeline[].summary | 이벤트 요약 | String | N | "02:05 생명 유지 시스템에서..." |
| data.facts[].factId | 사실 ID | String | N | "FACT-TRIGGER" |
| data.facts[].kind | 사실 종류 (`ACTION`/`MOTIVE`/`ALIBI`/`EXCLUSION`/`CONDITION`/`CLAIM`) | String | N | "ACTION" |
| data.facts[].statement | 사실 내용 | String | N | "02:05 소피아 인증으로..." |
| data.facts[].truthValue | 실제로 참인 사실인지(거짓 주장인 CLAIM도 포함될 수 있음) | boolean | N | true |
| data.alibis[].characterId | 인물 ID | String | N | "SOPHIA" |
| data.alibis[].initialClaim | 처음 진술한 알리바이 | String | N | "그 시간엔 자고 있었다." |
| data.alibis[].actualWhereabouts | 실제 행적 | String | N | "LIFE_SUPPORT_CORRIDOR" |
| data.solution.culpritId | 정답 범인 | String | N | "SOPHIA" |
| data.solution.requiredEvidenceByRole | 역할별 정답 단서 ID 매핑 | Object&lt;String, String[]&gt; | N | {"SETUP":["CLUE-SETUP-LOG"], ...} |
| data.solution.nonCulpritExclusions[].characterId | 용의자에서 제외된 인물 | String | N | "MARCUS" |
| data.solution.nonCulpritExclusions[].reason | 제외 사유 | String | N | "마커스는 접근 권한이 없었다." |

**Example**

```jsx
{
  "success": true,
  "message": null,
  "data": {
    "sessionId": "game_ec3c9086655e4179842cc9e851673a7f",
    "culpritId": "SOPHIA",
    "truthSummary": "소피아가 의료 안전 점검을 가장해 생명 유지 시스템을 정지시켰다.",
    "method": {
      "fictionalSummary": "안전 점검을 가장해 트리거를 심었다.",
      "setupAction": {
        "actorId": "SOPHIA",
        "locationId": "MEDICAL_BAY",
        "systemId": "LIFE_SUPPORT",
        "operation": "SCHEDULE_SAFETY_CHECK",
        "requiredCapabilityIds": ["MEDICAL_ACCESS"]
      },
      "triggerAction": {
        "actorId": "SOPHIA",
        "locationId": "LIFE_SUPPORT_CORRIDOR",
        "systemId": "LIFE_SUPPORT",
        "operation": "RUN_SAFETY_DIAGNOSTIC",
        "requiredCapabilityIds": ["MEDICAL_ACCESS"]
      },
      "victimCondition": "생명 유지 장치 의존 상태"
    },
    "timeline": [
      {
        "eventId": "EVENT-TRIGGER",
        "time": "02:05",
        "actorIds": ["SOPHIA"],
        "locationId": "LIFE_SUPPORT_CORRIDOR",
        "actionType": "SYSTEM_ACTION",
        "summary": "02:05 생명 유지 시스템에서 의료 안전 진단이 실행됐다.",
        "factIds": ["FACT-TRIGGER"]
      }
    ],
    "facts": [
      {
        "factId": "FACT-TRIGGER",
        "kind": "ACTION",
        "statement": "02:05 소피아 인증으로 안전 진단이 실행됐다.",
        "truthValue": true,
        "subjectCharacterIds": ["SOPHIA"]
      }
    ],
    "alibis": [
      {
        "characterId": "SOPHIA",
        "initialClaim": "그 시간엔 자고 있었다.",
        "actualWhereabouts": "LIFE_SUPPORT_CORRIDOR",
        "supportingFactIds": [],
        "contradictingFactIds": ["FACT-TRIGGER"]
      }
    ],
    "solution": {
      "culpritId": "SOPHIA",
      "requiredEvidenceByRole": {
        "SETUP": ["CLUE-SETUP-LOG"],
        "TRIGGER": ["CLUE-TRIGGER-LOG"],
        "OPPORTUNITY": ["CLUE-ACCESS-HISTORY"],
        "MOTIVE": ["CLUE-MOTIVE-MESSAGE"],
        "VICTIM_CONDITION": []
      },
      "acceptedAlternativesByRole": {
        "SETUP": [], "TRIGGER": [], "OPPORTUNITY": [], "MOTIVE": [], "VICTIM_CONDITION": []
      },
      "nonCulpritExclusions": [
        { "characterId": "MARCUS", "excludedByClueIds": ["CLUE-ACCESS-HISTORY"], "reason": "마커스는 접근 권한이 없었다." }
      ]
    }
  }
}
```

### Status

| status | response content |
| --- | --- |
| 200 | 조회 성공 |
| 404 | 존재하지 않는 sessionId |
| 409 | 아직 `COMPLETED`도 `INCORRECT`도 아님(게임이 아직 진행 중) |

**프론트 구현 팁:** 이 API는 게임 끝나고 딱 한 번 호출하면 되는 "전체 재구성" 데이터라 필드가 많습니다. 엔딩 화면을 여러 섹션(수법/타임라인/알리바이/최종 근거)으로 나눠서 순서대로 보여주는 걸 추천합니다.
