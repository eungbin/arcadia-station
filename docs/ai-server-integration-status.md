# 백엔드 작업 현황 & AI 서버 연동 준비 상태

> 작성일: 2026-07-29
> 대상: AI 서버팀 공유용

## 1. 한 줄 요약

`docs/arcadia-station-backend-spec.md`(4·5·6장 확정 계약)에 정의된 백엔드 스코프를 전부 구현했고, Fake AI 클라이언트로 전체 게임 플로우(세션 생성→탐사→RAG→심문→추리 판정→결과 공개)가 실제로 동작하는 것까지 확인했습니다. **AI 서버 쪽 배포 URL과 인증 키만 확정되면, 저희 쪽은 설정값 하나(`real-ai` 프로파일 활성화)만 바꿔서 바로 실제 연동을 시도할 수 있는 상태입니다.**

## 2. 백엔드 구현 완료 항목

| 영역 | 상태 |
|---|---|
| 세션 생명주기(`GameSession`, 상태 전이) | 완료, DB(PostgreSQL) 영속화 |
| `CaseBlueprint` 역직렬화(`case-blueprint.schema.json` 스키마 그대로) | 완료, 필드명 1:1 매칭 확인 |
| 탐사(EXPLORE) / CONNECT 연쇄 해금 | 완료 |
| NPC 심문 프록시 + `revealedFactIds` 화이트리스트 재검증 | 완료 |
| RAG 검색 프록시 + `citedRecordIds`/`newlyDiscoveredClues` 재검증 | 완료 |
| 최종 추리 판정(LLM 미호출, 순수 Java 로직) | 완료 |
| 프론트 공개 API(`PlayerCaseView` 등 스포일러 차단) | 완료, 회귀 테스트로 고정 |
| AI 서버 실제 연동용 클라이언트(WebClient 기반) | **구현 완료, 실제 서버 미배포로 미검증** |
| 4.4/4.5절 폴링 백오프 + 자동 재시도(최대 2회) → FAILED | 완료 |
| 404 `SESSION_NOT_FOUND`(AI 서버 세션 유실) 방어 처리 | 완료 |
| Render Keep-Alive 스케줄러(12.2절) | 완료 |

테스트: 단위/통합 테스트 36개 전부 통과. 실제로 로컬에서 서버를 띄워 세션 생성 → 탐사 → RAG → 심문 → 판정 → 결과 공개까지 curl로 수동 검증도 완료했습니다.

## 3. 저희가 실제로 보낼 요청 / 기대하는 응답

아래는 저희 백엔드의 `RealCaseGenerationClient` / `RealInterrogationClient` / `RealAssistantClient`가 실제로 구현된 그대로의 스펙입니다. 서버 쪽 응답이 이 형태와 다르면 저희 쪽에서 파싱이 깨지니, 필드명·타입이 정확히 일치하는지 확인 부탁드립니다.

### 3.1 사건 생성 요청

```http
POST {AI_SERVER_BASE_URL}/internal/v1/cases
X-Internal-AI-Key: <사전 공유 키>
Content-Type: application/json

{ "sessionId": "req_xxxxxxxx", "seed": "optional-seed" }
```

- `sessionId`는 저희 쪽 `aiCaseRequestId`입니다(플레이어가 보는 세션 ID와는 다른 값). 재시도 시마다 새 값으로 보냅니다.
- 응답(202): `{ "sessionId": "...", "status": "CREATING", "statusUrl": "..." }`

### 3.2 사건 상태 폴링

```http
GET {AI_SERVER_BASE_URL}/internal/v1/cases/{sessionId}
X-Internal-AI-Key: <사전 공유 키>
```

진행 중: `{ "sessionId": "...", "status": "VALIDATING", "generation": null, "errorCode": null }`

완료(`READY`) 시:
```json
{
  "sessionId": "...",
  "status": "READY",
  "generation": {
    "caseBlueprint": { "...case-blueprint.schema.json 전체..." },
    "blueprintSha256": "...",
    "generationAttemptCount": 1,
    "generationSource": "AI",
    "model": "...",
    "promptVersion": "...",
    "createdAt": "2026-07-29T00:00:00Z",
    "frozenAt": "2026-07-29T00:00:55Z"
  },
  "errorCode": null
}
```

저희는 0~30초 2초 간격, 30~90초 3초 간격, 90~210초 5초 간격(+지터)으로 폴링하고, 210초 넘으면 재시도(최대 2회) 후 `FAILED` 처리합니다.

### 3.3 NPC 심문

```http
POST {AI_SERVER_BASE_URL}/api/v1/sessions/{sessionId}/interrogations/{characterId}/turns
X-Internal-AI-Key: <사전 공유 키>
Content-Type: application/json

{ "question": "그 기록을 설명해 주세요.", "presentedClueIds": ["CLUE-TRIGGER-LOG"] }
```

기대 응답:
```json
{
  "dialogue": "...",
  "emotion": "DEFENSIVE",
  "revealedFactIds": ["FACT-TRIGGER"],
  "recommendedQuestions": [
    { "topicId": "TOPIC-1", "label": "..." },
    { "topicId": "TOPIC-2", "label": "..." }
  ]
}
```
(`recommendedQuestions`는 2개 고정으로 알고 있습니다.)

- `sessionId` 미존재(AI 서버 세션 유실) 시 404를 주시면, 저희 쪽에서 게임을 중단시키지 않고 "지금은 대답할 수 없습니다" 안전 응답으로 자동 대체합니다(이미 구현·테스트 완료).
- `revealedFactIds`는 저희가 `npcKnowledge.revealPolicies` 기준으로 다시 검증하고, 화이트리스트를 벗어나면 통째로 무시합니다. AI 서버 쪽에서 과공개하더라도 플레이어에게는 새어나가지 않습니다. 이 화이트리스트는 이번 턴뿐 아니라 **그 세션에서 지금까지 누적으로 제시했던 단서 전체**를 기준으로 계산합니다(이전 턴에 제시한 단서를 매번 다시 보내지 않아도 됩니다).

### 3.4 RAG 사건기록 검색

```http
POST {AI_SERVER_BASE_URL}/api/v1/sessions/{sessionId}/assistant/queries
X-Internal-AI-Key: <사전 공유 키>
Content-Type: application/json

{ "question": "02:05 안전 진단 기록을 보여줘" }
```

기대 응답:
```json
{
  "answer": "...",
  "citedRecordIds": ["RECORD-TRIGGER"],
  "suggestedQueries": ["...", "..."],
  "newlyDiscoveredClues": [
    { "clueId": "CLUE-TRIGGER-LOG", "title": "...", "clueType": "DIGITAL", "solutionRoles": ["TRIGGER"], "playerText": "..." }
  ]
}
```
(`suggestedQueries`도 2개 고정으로 알고 있습니다.)

- `newlyDiscoveredClues`는 저희가 `clueId` 기준으로만 신뢰하고, 나머지 필드(title/clueType/playerText 등)는 저희가 이미 갖고 있는 `CaseBlueprint`에서 다시 조회해서 씁니다. `clueId`가 저희 쪽 `CaseBlueprint.clues`에 없거나 `acquisition.type`이 `RAG_QUERY`가 아니면 무시하고 로그만 남깁니다.

## 4. 저희 쪽에서 이미 준비된 방어 로직 (AI 서버 상태와 무관하게 동작)

- `X-Internal-AI-Key` 헤더는 3개 엔드포인트 전부에 항상 보냅니다(현재 NPC/RAG는 미검사인 것으로 알고 있고, 나중에 검사가 붙어도 저희 쪽 코드 변경 필요 없음).
- NPC/RAG에서 404(세션 유실)를 받으면 게임을 중단시키지 않고 안전 응답으로 대체 + 내부 플래그(`AI_SESSION_LOST`)로 운영 로그 추적.
- 사건 생성 통신 실패 시 자동 재시도(최대 2회) 후 `FAILED` 확정, 프론트에는 스포일러 없는 일반 오류만 전달. 첫 번째 시도가 실패해도 세션 자체는 유실되지 않고 재시도 대상으로 남습니다.
- 빈 질문(`question`)은 AI 서버에 보내기 전에 백엔드 단에서 먼저 거릅니다.

## 5. AI 서버팀께 확인 부탁드리는 사항

1. **배포 URL(+ Public/Private Service 여부)** — 현재 로컬(`http://127.0.0.1:8081`)만 알고 있습니다. 12.1절 권장 토폴로지대로 AI 서버를 Render Private Service로 배포하실 계획이라면, 저희 백엔드도 같은 Render workspace/region에 있어야 사설망 통신이 가능합니다. (a) Public/Private 중 어느 쪽으로 배포하실지, (b) Private이라면 저희가 같은 workspace에 들어가야 하는지와 private hostname 형식을 알려주시면 저희 쪽 배포 설정에 반영하겠습니다.
2. **`X-Internal-AI-Key` 실제 값** — 사전 공유 시크릿이라 양쪽이 같은 값을 넣어야 합니다. 확정되면 저희도 환경변수에 반영하겠습니다.
3. **NPC/RAG 응답 필드명이 위 3.3/3.4절과 정확히 일치하는지** — 특히 `emotion`/`clueType`/`solutionRoles` 같은 enum 값의 대소문자·표기가 저희 쪽 enum(`CALM/DEFENSIVE/ANXIOUS/ANGRY/EVASIVE`, `PHYSICAL/DIGITAL/MOTIVE/OPPORTUNITY` 등)과 정확히 같은지 한 번 맞춰보면 좋을 것 같습니다.
4. **세션 영속화(16.2절 P0) 진행 상황** — 데모 중 재배포되면 진행 중인 세션이 전부 유실되는 부분, 일정 공유 부탁드립니다.
5. **`acquisition.type`에 `INTERROGATE`/`AUTO`를 실제로 쓰시는지** — 저희 백엔드는 현재 `EXPLORE`(탐사)/`RAG_QUERY`(검색)/`CONNECT`(연쇄) 세 가지 타입만 해금 로직을 구현했습니다. 만약 생성되는 CaseBlueprint에 `INTERROGATE`나 `AUTO` 타입 단서가 실제로 포함된다면, 그 단서는 저희 쪽에 해금 트리거가 없어서 **영원히 잠긴 채로 남습니다.** 두 타입을 실제로 생성에 쓰신다면 어떤 조건으로 해금돼야 하는지 알려주시면 저희 쪽에 로직을 추가하겠습니다. (안 쓰신다면 무시하셔도 됩니다.)
6. **`case-blueprint.schema.json` 원본 파일 공유 가능할까요** — 저희는 기획서(markdown)에 옮겨 적힌 Java 의사코드를 보고 `CaseBlueprint` 및 하위 25개 record/enum을 직접 작성했습니다. 필드명 오타 하나로 역직렬화가 통째로 깨지는 구조라, 원본 JSON Schema 파일을 주시면 저희 쪽 테스트에 스키마 검증기를 붙여서 완전히 동일한지 기계적으로 확인하고 싶습니다.
7. **AI 서버 쪽 에러 응답 바디 형식** — 5.4/6.3절에 에러 코드 목록(`INVALID_REQUEST` 등)은 있는데, 실제 400/409 응답의 JSON 바디 형식(예: `{"code": "...", "message": "..."}`)은 아직 못 봤습니다. 저희 쪽 검증이 대부분 선행되긴 하지만, 참고로 알려주시면 좋겠습니다.
8. **폴링 계약 필드화(`Retry-After`/`pollAfterMs`, 16.3절 P1) 일정** — 지금은 저희가 자체 백오프 스케줄(2s/3s/5s)로 폴링하고 있는데, 서버가 권장 간격을 응답에 실어주는 방식으로 바뀔 계획이 있다면 저희도 그에 맞춰 폴링 로직을 단순화할 수 있을 것 같습니다.
9. **staging 30건 이상 성능 측정(p50/p95, 16.3절 P1) 결과** — 현재 저희 타임아웃(사건 생성 60초, NPC/RAG 65초)은 샘플 1건(55.39초) 기준입니다. 측정치가 나오면 타임아웃 값을 다시 맞춰보고 싶습니다.
10. **`redHerrings`를 백엔드가 능동적으로 다뤄야 하는지** — 현재 저희는 `redHerrings`를 그대로 저장만 하고 별도 API 로직은 없습니다. 프론트 노출이나 판정에 관여할 부분이 있다면 알려주세요.
11. **NPC/RAG 엔드포인트에도 호출 빈도 제한이 있는지** — `GET /internal/v1/cases/{id}`는 빈도 제한이 없다고 확인해주셨는데, 플레이어가 짧은 시간에 여러 번 심문·검색을 시도할 수 있는 NPC/RAG 두 엔드포인트에도 동일하게 제한이 없는지, 혹은 비용상 주의할 게 있는지 궁금합니다.
12. **사건 생성 폴링 응답의 `errorCode` 값 목록** — `GET /internal/v1/cases/{id}` 응답에 `errorCode` 필드가 있는데, 실제로 어떤 값들이 들어올 수 있는지(예: 검증 실패 사유별 코드 등) 예시가 아직 없습니다. 목록을 공유해주시면 저희 쪽 운영 로그에 더 구체적으로 남길 수 있을 것 같습니다.

## 6. 실제 연동 전환 방법 (참고)

저희 쪽은 `application.yml`의 `arcadia.ai-server.base-url` / `internal-api-key`만 채우고, 배포 시 `real-ai` 스프링 프로파일만 켜면 Fake 클라이언트 대신 실제 클라이언트로 자동 전환됩니다. 코드 변경 없이 설정만으로 스위칭 가능한 상태라, URL/키만 주시면 바로 붙여볼 수 있습니다.

---

## 7. AI 서버팀 회신 반영 (2026-07-30)

전체 회신 원문: [`docs/ai-server-integration-response.md`](./ai-server-integration-response.md)

**`real-ai` 프로파일은 여전히 비활성 상태(기본값)** — AI 서버팀 요청대로 URL·인증·세션 영속화 P0가 끝날 때까지 켜지 않는다.

회신에서 확인된 계약 차이 중 백엔드 코드 수정이 필요했던 두 가지를 반영 완료:

- NPC/RAG 호출 시 플레이어 `sessionId`가 아니라 사건 생성 때 쓴 `aiCaseRequestId`를 전달하도록 수정(회신 3.1절). `GameSessionRepository.findByAiCaseRequestId` 추가.
- AI 서버가 NPC 턴 이력을 누적하지 않는다는 확인에 따라(회신 3.2절), 그 NPC에게 지금까지 제시한 단서 전체(누적)를 매 요청에 보내도록 수정. `EvidenceInventory`도 NPC별로 제시 이력을 분리하도록 보강.
- 실제 READY 응답의 `generation` 객체에 있는 추가 필드(`blueprintId`/`seed`/`worldTemplate`/`ruleTemplate`, 회신 3.3절)는 Jackson 기본 동작으로 이미 무시되고 있음을 실제 응답 예시로 확인.
- AI 서버팀이 전달한 원본 `case-blueprint.schema.json`으로 우리 `CaseBlueprint` 역직렬화 결과를 기계적으로 검증하는 테스트 추가 — 통과 확인.

추가 수정 없이 그대로 유효한 항목: `errorCode`는 `FAILED` 상태일 때만 `CASE_GENERATION_FAILED`(그 외 `null`), `redHerrings`는 저장만, `INTERROGATE`/`AUTO` 타입은 staging에서 생성 자체를 배제하기로 함(백엔드 쪽 추가 구현 불필요), `suggestedQueries`/`recommendedQuestions` 배열 길이는 원래부터 유연하게 처리 중.

여전히 AI 서버팀 쪽 P0로 남아있어 백엔드가 기다리는 항목: Render 배포/URL 확정, 3개 엔드포인트 내부 인증 통일, AI 세션·RAG 인덱스 영속화, staging 생성 타입을 `EXPLORE`/`RAG_QUERY`/`CONNECT`로 제한.
