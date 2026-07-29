# AI 서버 연동 확인 결과 및 백엔드 회신안

> 작성일: 2026-07-29
> 검토 대상: `ai-server-integration-status.md`
> AI 서버 기준 커밋: `d069d7b` (`agent/frontend-integration-contract`)

## 1. 결론

백엔드는 Fake AI 기준 게임 플로우와 실제 AI 클라이언트 구현을 완료했지만, **현재 바로 `real-ai` 프로파일을 켜면 안 됩니다.**

실제 연동 전에 AI 서버에서 끝내야 할 P0 작업은 다음 네 가지입니다.

1. Render 배포 방식과 실제 base URL 확정
2. 사건 생성/NPC/RAG 세 엔드포인트의 내부 인증 통일
3. AI 세션과 RAG 인덱스 영속화 또는 재구성 방식 구현
4. 백엔드에 해금 로직이 없는 `INTERROGATE`/`AUTO` 단서가 생성되지 않도록 제한

또한 양쪽 구현 사이에 아래 계약 차이가 확인됐습니다.

- 사건 생성에 쓴 `aiCaseRequestId`를 폴링뿐 아니라 이후 NPC/RAG에도 동일하게 사용해야 합니다.
- 현재 AI 서버는 NPC 턴마다 받은 `presentedClueIds`만 평가합니다. 이전 턴 제시 기록을 AI 서버가 누적하지 않으므로, 백엔드는 **그 세션에서 지금까지 제시한 단서 ID 전체를 매 요청에 보내야 합니다.**
- 실제 `generation` 객체에는 백엔드 예시에서 빠진 `blueprintId`, `seed`, `worldTemplate`, `ruleTemplate`이 추가로 들어갑니다.
- `suggestedQueries`는 AI 구조화 응답에서는 2개지만, 현재 결정론적 fallback 경로에서는 0~2개가 될 수 있습니다. AI 서버에서 2개 보장 패치가 필요합니다.

---

## 2. AI 서버가 지금 해야 할 작업

### P0: 실제 연동 전에 필수

| 작업 | 현재 상태 | 완료 기준 |
|---|---|---|
| Render 서비스 생성 및 URL 확정 | 미배포 | staging AI 서비스 생성, `/actuator/health` 성공, 백엔드에서 연결 확인 |
| 내부 인증 통일 | 사건 생성/조회만 검사 | 사건 생성·조회, NPC, RAG 모두 동일한 인증 필터와 403 계약 적용 |
| 세션 영속화 | `ConcurrentHashMap` 메모리 저장 | 재시작/재배포 후 같은 AI `sessionId`로 NPC/RAG 호출 성공 |
| 단서 획득 타입 제한 | `INTERROGATE`, `AUTO` 생성 가능 | staging 생성 결과는 `EXPLORE`, `RAG_QUERY`, `CONNECT`만 사용 |
| 응답 배열 길이 보장 | 일부 fallback에서 미보장 | `recommendedQuestions`와 `suggestedQueries`를 항상 2개로 반환 |
| 계약 통합 테스트 | AI 단독 테스트만 존재 | 백엔드가 제시한 요청과 전체 응답 fixture로 양쪽 파싱 테스트 통과 |

### P1: staging 안정화

| 작업 | 이유 |
|---|---|
| 403을 포함한 오류 응답 형식 통일 | 현재 400/404/409와 403의 body 형식이 다를 수 있음 |
| `Retry-After` 또는 `pollAfterMs` 제공 | 서버가 권장 폴링 간격을 계약으로 전달 |
| 목적별 HTTP timeout 분리 | 현재 Gemini/OpenAI 클라이언트는 사건 생성용 60초 timeout을 NPC/RAG에도 공통 사용 |
| NPC/RAG 호출 제한 및 429 계약 추가 | 비용·중복 요청·동시 요청 제어 |
| staging 사건 30건 이상 측정 | p50, p95, 최대값, fallback 비율을 근거로 timeout 재조정 |
| 내부 키 무중단 로테이션 | staging/production 키 분리 및 current/previous 이중 검증 |

### P2: 후속 개선

- 관측 지표에 `generationSource`, validation issue, provider quota cooldown, NPC/RAG fallback 횟수 추가
- 계약 버전 헤더 또는 `/internal/v1/info` 제공
- AI 세션 복구 API를 사용할 경우 스키마와 인증 정책 별도 정의

---

## 3. 백엔드에 요청해야 할 변경 및 확인

### 3.1 AI sessionId 매핑

`POST /internal/v1/cases`에 전달한 `sessionId`가 AI 서버의 세션 기본 키가 됩니다.

백엔드는 다음 매핑을 DB에 보관해야 합니다.

```text
playerSessionId -> aiCaseRequestId
```

아래 세 요청에는 모두 같은 `aiCaseRequestId`를 사용해야 합니다.

```text
GET  /internal/v1/cases/{aiCaseRequestId}
POST /api/v1/sessions/{aiCaseRequestId}/interrogations/{characterId}/turns
POST /api/v1/sessions/{aiCaseRequestId}/assistant/queries
```

재시도로 새 `aiCaseRequestId`를 발급했다면, 최종적으로 `READY`가 된 ID를 이후 NPC/RAG에 사용해야 합니다.

### 3.2 NPC의 presentedClueIds

현재 AI 서버는 NPC 심문 이력을 별도로 저장하지 않고, **이번 요청에 포함된 `presentedClueIds`만** `revealPolicies.requiredPresentedClueIds`와 비교합니다.

따라서 백엔드는 새로 제시한 단서만 보내지 말고, 그 NPC에게 지금까지 제시한 단서의 누적 목록을 매번 보내야 합니다.

```json
{
  "question": "앞서 보여드린 두 기록을 함께 설명해 주세요.",
  "presentedClueIds": [
    "CLUE-SETUP-PANEL",
    "CLUE-TRIGGER-LOG"
  ]
}
```

### 3.3 READY generation 객체

백엔드 문서의 예시보다 실제 응답에 메타 필드가 네 개 더 있습니다.

```json
{
  "generation": {
    "blueprintId": "CASE-...",
    "seed": "...",
    "worldTemplate": {
      "id": "ARCADIA_WORLD",
      "version": "1.0.0"
    },
    "ruleTemplate": {
      "id": "ARCADIA_MYSTERY_RULES",
      "version": "1.0.0"
    },
    "blueprintSha256": "...",
    "generationAttemptCount": 1,
    "generationSource": "AI",
    "model": "...",
    "promptVersion": "case-generator-v1",
    "createdAt": "...",
    "frozenAt": "...",
    "caseBlueprint": {}
  }
}
```

백엔드 DTO에 네 필드를 추가하거나, 최소한 알 수 없는 필드 때문에 역직렬화가 실패하지 않도록 설정해야 합니다.

`generationSource`가 `FALLBACK`이더라도 `status=READY`이면 정상 게임을 시작하면 됩니다.

---

## 4. 백엔드의 12개 질문에 대한 답변

### 1) 배포 URL과 Public/Private Service

**현재 답변:** 실제 staging/production URL은 아직 생성되지 않았습니다. 따라서 현재는 `real-ai` 프로파일을 활성화하면 안 됩니다.

권장 구성은 같은 Render workspace와 region에 다음처럼 배치하는 것입니다.

```text
Backend Web Service
        |
        | Render Private Network
        v
AI Private Service
```

Render 사설망 통신은 같은 workspace와 region에 있는 서비스끼리 가능하며, Private Service는 공개 `onrender.com` 주소가 없습니다. 실제 private hostname은 서비스를 만든 뒤 Render Dashboard의 Internal/Connect 정보로 공유해야 합니다.

- Render Private Network: https://render.com/docs/private-network
- Render Private Services: https://render.com/docs/private-services

Free Web Service는 15분 유휴 시 정지되고 재기동에 약 1분이 걸리며 private network 요청을 받을 수 없으므로, 운영 AI 서버 용도로는 적합하지 않습니다.

- Render Free limitations: https://render.com/docs/free

**결정 필요:** 백엔드가 현재 다른 workspace/region에 있다면 같은 위치로 이동·초대할지, staging만 임시 Public Web Service로 열지 프로젝트 차원의 결정이 필요합니다.

### 2) X-Internal-AI-Key 실제 값

**현재 답변:** 아직 staging/production 실제 키를 발급하거나 공유하지 않았습니다.

현재 코드에서는 `AI_INTERNAL_API_KEY`가 비어 있으면 인증 검사가 사실상 꺼지고, 값이 있어도 `/internal/v1/cases` 생성/조회에서만 검사합니다. NPC/RAG에는 아직 검사가 없습니다.

AI 서버에서 세 엔드포인트 인증을 통일한 후 아래 정책으로 전달해야 합니다.

- staging/production 키 분리
- 최소 32바이트 이상의 난수
- Git, Markdown, PR, 프론트 환경변수에 기록 금지
- Render Secret 또는 환경변수에 저장
- 실제 값은 비공개 채널로 1회 공유

### 3) NPC/RAG 응답 필드와 enum

필드명과 enum 표기는 백엔드 문서와 일치합니다.

NPC:

- `dialogue: string`
- `emotion: CALM | DEFENSIVE | ANXIOUS | ANGRY | EVASIVE`
- `revealedFactIds: string[]`
- `recommendedQuestions: {topicId, label}[]`

RAG:

- `answer: string`
- `citedRecordIds: string[]`
- `suggestedQueries: string[]`
- `newlyDiscoveredClues: PublicClueView[]`
- `clueType: PHYSICAL | DIGITAL | MOTIVE | OPPORTUNITY`
- `solutionRoles: SETUP | TRIGGER | OPPORTUNITY | MOTIVE | VICTIM_CONDITION`

`recommendedQuestions`는 현재 guard와 AI 응답 스키마에서 2개를 요구합니다. `suggestedQueries`는 AI 구조화 응답 스키마에서는 2개지만 결정론적 fallback이 항상 2개를 보장하지는 않습니다. staging 전 AI 서버가 둘 다 정확히 2개로 정규화하겠습니다. 백엔드도 배열 길이가 달라도 파싱 자체는 실패하지 않게 두는 것을 권장합니다.

### 4) 세션 영속화 진행 상황

**미구현입니다.**

현재 `InMemoryGameSessionRepository`와 메모리 RAG 인덱스를 사용합니다. 프로세스 재시작, 재배포, 인스턴스 교체 시 다음이 모두 사라집니다.

- Frozen `CaseBlueprint`
- 발견 단서 목록
- 오답 횟수와 세션 상태
- RAG 임베딩 인덱스

백엔드가 CaseBlueprint를 DB에 저장해도 현재 AI 서버에는 재주입 API가 없으므로 자동 복구되지 않습니다. 실제 연동 전 P0로 처리해야 합니다.

### 5) INTERROGATE/AUTO 획득 타입

현재 안전 fallback 사건은 `EXPLORE`와 `RAG_QUERY`만 사용합니다. 그러나 생성 프롬프트와 active rule template은 `INTERROGATE`와 `AUTO`도 허용합니다.

현재 AI 서버 심문 로직은 fact만 공개하고 `INTERROGATE` 단서를 evidence inventory에 추가하지 않으므로, 이 타입이 생성되면 백엔드 설명처럼 영구 잠금될 수 있습니다. `AUTO`도 명시적인 해금 시점이 없습니다.

**staging 계약:** 당장은 AI 생성 허용 타입을 `EXPLORE`, `RAG_QUERY`, `CONNECT`로 제한합니다. 백엔드는 `INTERROGATE`/`AUTO` 로직을 지금 추가하지 않아도 됩니다. 추후 두 타입이 필요해지면 해금 이벤트와 응답 DTO를 별도 합의합니다.

### 6) case-blueprint.schema.json 공유

원본 파일을 그대로 전달할 수 있습니다.

- 저장소 원본: `src/main/resources/ai/schema/case-blueprint.schema.json`
- 전달용 복사본: `output/pdf/case-blueprint.schema.json`
- SHA-256: `0d4544262bd279d2c910453e68679cf1c13c77e688c51a96cf5df3715045deca`

JSON Schema Draft 2020-12이며 주요 object에는 `additionalProperties: false`가 적용됩니다. 백엔드 테스트에서 원본 스키마 검증을 추가하는 방향에 동의합니다.

### 7) AI 서버 오류 응답 body

현재 공통 처리되는 400/404/409 응답 형식은 다음과 같습니다.

```json
{
  "timestamp": "2026-07-29T00:00:00Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "오류 설명"
}
```

| HTTP | code | 의미 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 빈 질문, 잘못된 ID, 미발견 단서 제시 등 |
| 404 | `SESSION_NOT_FOUND` | AI 서버에 세션 없음 |
| 409 | `SESSION_NOT_READY` | 사건 동결 전 NPC/RAG 호출 |
| 409 | `INVALID_SESSION_STATE` | 중복 세션 ID 또는 잘못된 상태 전이 |

현재 내부 키 실패는 `ResponseStatusException(403)`으로 처리되어 위 `ApiError` 형식과 body가 다를 수 있습니다. P1이 아니라 실제 연동 전 수정 대상으로 올려, 403도 다음처럼 통일하는 편이 안전합니다.

```json
{
  "timestamp": "2026-07-29T00:00:00Z",
  "status": 403,
  "code": "INVALID_INTERNAL_API_KEY",
  "message": "Invalid internal API key"
}
```

메시지 문자열은 운영 로그 참고용이며, 백엔드 분기는 HTTP status와 `code`를 기준으로 해야 합니다.

### 8) Retry-After/pollAfterMs 일정

현재 AI 응답에는 `Retry-After` 헤더와 `pollAfterMs` 필드가 없습니다. 백엔드가 구현한 `2초 -> 3초 -> 5초 + jitter` 스케줄을 그대로 사용해도 됩니다.

서버 권장 주기 필드화는 P1로 진행하며, 계약을 바꾸기 전까지 백엔드 로직을 변경할 필요는 없습니다.

### 9) staging 30건 성능 측정

아직 30건 측정을 하지 않았습니다.

현재 확인된 실측은 Gemini 사건 생성 1건 약 `55.39초`이며, JSON 구조화 응답은 성공했지만 게임 의미 검증에서 탈락해 최종적으로 검증된 fallback 사건이 `READY`가 됐습니다. 샘플 1건은 p50/p95 근거로 사용할 수 없습니다.

추가 주의사항:

- provider HTTP read/connect timeout은 현재 목적 구분 없이 60초입니다.
- 사건 생성은 최대 3회 시도할 수 있어 provider 대기만 약 180초가 될 수 있습니다.
- `arcadia.ai.npc.timeout=8s` 설정은 존재하지만 현재 provider HTTP 클라이언트에 적용되지 않습니다.

staging 배포 후 최소 30건으로 `p50`, `p95`, 최대값, `generationSource=FALLBACK` 비율을 측정해 공유해야 합니다.

### 10) redHerrings 처리

현재 백엔드는 `redHerrings`를 저장만 하면 됩니다.

AI 서버에서는 다음 목적으로 사용합니다.

- 미끼 정보가 반드시 해소 가능한지 생성 결과 검증
- `resolutionFactIds` 참조 무결성 검증
- 사건 설계 메타데이터 보존

현재 별도 해금 API나 최종 판정 로직은 없습니다. 게임 종료 전 `resolutionFactIds`를 프론트에 공개하면 안 됩니다. 추후 프론트에 `presentation`을 능동 노출할 필요가 생기면 별도 공개 규칙을 합의합니다.

### 11) NPC/RAG 호출 빈도 제한

현재 HTTP 레벨 rate limit은 없습니다.

온라인 AI 모드에서는:

- NPC 요청은 텍스트 생성 1회를 사용할 수 있습니다.
- RAG 요청은 query embedding과 요약 생성을 각각 사용할 수 있습니다.
- provider quota 오류가 발생하면 10분 cooldown을 열고 안전 fallback으로 전환합니다.

staging 임시 권고는 세션별 NPC/RAG 각각 동시 요청 1개, 동일 질문 중복 전송 방지입니다. 구체적인 초당 제한과 429 응답 계약은 사용량 측정 후 확정합니다.

### 12) 사건 상태 errorCode 목록

현재 `GET /internal/v1/cases/{sessionId}`의 `errorCode`에 들어갈 수 있는 값은 하나입니다.

| status | errorCode |
|---|---|
| `CREATING`, `VALIDATING`, `READY` | `null` |
| `FAILED` | `CASE_GENERATION_FAILED` |

모델 timeout, quota 부족, JSON 변환 실패, 의미 검증 실패는 가능한 경우 검증된 fallback 사건으로 전환되므로 보통 `READY`, `generationSource=FALLBACK`, `errorCode=null`이 됩니다.

`INVALID_REQUEST`, `SESSION_NOT_FOUND`, `SESSION_NOT_READY` 등은 상태 폴링의 `errorCode`가 아니라 해당 HTTP 오류 응답 body의 `code`입니다.

---

## 5. 백엔드에 지금 전달할 파일

반드시 전달:

1. `ai-server-integration-response.md` - 이 답변과 양쪽 작업 목록
2. `src/main/resources/ai/schema/case-blueprint.schema.json` - 역직렬화/스키마 검증 원본
3. `docs/examples/internal-case-ready.response.json` - 필드 전체가 포함된 READY 응답
4. `docs/AI_BACKEND_CONTRACT.md` - 사건 생성·폴링 상세 계약

편의용 묶음:

- `output/pdf/arcadia-ai-backend-handoff-2026-07-28.zip`
- `output/pdf/arcadia-ai-backend-integration-qa-ko.pdf`

보안상 파일로 전달하면 안 되는 것:

- `X-Internal-AI-Key` 실제 값
- Gemini/OpenAI API 키
- Render secret과 production 환경변수

URL과 내부 키는 AI 서버의 인증·배포 작업이 끝난 뒤 비공개 채널로 따로 전달해야 합니다.

---

## 6. 백엔드에 바로 보낼 회신 문안

아래 내용을 그대로 보내도 됩니다.

```text
상세한 연동 현황 공유 감사합니다. 현재 AI 서버 코드와 대조한 결과를 전달드립니다.

먼저 현재는 실제 Render URL과 공용 내부 인증, 세션 영속화가 아직 완료되지 않아
real-ai 프로파일은 아직 활성화하지 말아 주세요. P0 작업 완료 후 staging URL과
X-Internal-AI-Key를 비공개 채널로 전달드리겠습니다.

요청/응답 필드와 enum은 대체로 일치합니다. 다만 실제 READY generation 객체에는
문서 예시 외에 blueprintId, seed, worldTemplate, ruleTemplate 필드도 포함됩니다.
첨부한 internal-case-ready.response.json을 전체 응답 기준으로 사용해 주세요.

중요한 sessionId 계약이 하나 있습니다. 사건 생성 요청에 사용한 aiCaseRequestId가
AI 서버의 세션 키이므로, 상태 폴링뿐 아니라 이후 NPC/RAG 요청에도 같은 ID를
사용해야 합니다. 플레이어 세션 ID와의 매핑을 유지해 주세요.

또한 현재 AI 서버는 NPC 턴별 presentedClueIds 이력을 누적하지 않습니다.
백엔드에서 이미 누적 제시 단서를 관리하고 있으므로, NPC 요청마다 그 NPC에게
지금까지 제시한 단서 ID 전체를 presentedClueIds로 보내 주세요.

recommendedQuestions는 2개 계약입니다. suggestedQueries는 AI 구조화 응답에서는
2개지만 fallback 경로가 현재 0~2개일 수 있어 AI 서버에서 2개 보장으로 보완하겠습니다.

INTERROGATE/AUTO 단서는 현재 양쪽 모두 완전한 해금 흐름이 없으므로, staging에서는
AI 생성 타입을 EXPLORE/RAG_QUERY/CONNECT로 제한하겠습니다. 따라서 백엔드는 당장
두 타입의 해금 로직을 추가하지 않아도 됩니다.

redHerrings는 현재 저장만 하시면 되고 별도 API 처리나 판정 로직은 필요 없습니다.
resolutionFactIds는 게임 종료 전 프론트에 공개하지 말아 주세요.

현재 상태 폴링 errorCode는 FAILED일 때 CASE_GENERATION_FAILED 하나이며,
그 외 INVALID_REQUEST/SESSION_NOT_FOUND 등의 값은 HTTP 오류 body의 code입니다.

원본 case-blueprint.schema.json과 전체 READY 응답 예시를 함께 전달드립니다.
배포/인증/영속화 완료 후 실제 연동 테스트 일정을 잡겠습니다.
```

---

## 7. 검토 근거와 검증 상태

- AI 서버 소스 기준 커밋: `d069d7b`
- 기존 Surefire 결과: 22 tests, 0 failures, 0 errors, 0 skipped
- Surefire 결과 생성일: 2026-07-28
- 이번 검토 환경에는 Maven 실행 파일이 없어 테스트를 새로 실행하지는 못했으며, 기존 `target/surefire-reports`를 확인했습니다.
- 백엔드가 보고한 테스트 36개는 백엔드 저장소 결과이므로 AI 저장소에서 별도로 재검증하지 않았습니다.
