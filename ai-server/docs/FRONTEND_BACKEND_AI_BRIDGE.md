# 프론트 ↔ 게임 백엔드 ↔ AI 서버 연동

대상 저장소:

- 프론트: 모노레포 `hackathon-yaho/arcadia-station`의 `frontend/`
- AI 서버: 같은 모노레포의 `ai-server/`

세 프로젝트는 한 저장소에 있지만 서로 독립된 서비스로 실행·배포합니다. 브라우저가
AI 서버를 직접 호출하지 않고 게임 백엔드가 두 계약을 변환하는 구조로 연결합니다.

```text
React/Vite
  └─ /api/* ──> 게임 백엔드
                  ├─ 세션·일차·버전·투표 저장
                  └─ /internal/v1/cases 및 /api/v1/sessions/* ──> AI 서버
```

이 경계는 브라우저에 범인, 전체 사건, 미발견 단서와 AI 내부 키가 노출되는 것을
방지합니다.

## 개발 포트

| 프로세스 | 권장 주소 |
|---|---|
| 프론트 Vite | `http://127.0.0.1:5173` |
| 게임 백엔드 | `http://127.0.0.1:8080` |
| AI 서버 | `http://127.0.0.1:8081` |

AI 서버는 다음처럼 포트를 분리해 실행할 수 있습니다.

```powershell
$env:SERVER_PORT = '8081'
$env:AI_INTERNAL_API_KEY = '<shared-internal-secret>'
.\mvnw.cmd spring-boot:run
```

프론트의 개발 프록시는 `/api`를 게임 백엔드로 전달합니다.

```dotenv
VITE_API_MODE=http
VITE_API_BASE_URL=/api
VITE_API_PROXY_TARGET=http://127.0.0.1:8080
```

## 게임 시작

1. 프론트가 게임 백엔드에 `POST /api/sessions`를 호출합니다.
2. 백엔드는 세션 ID를 생성합니다.
3. 백엔드는 AI 서버에 `POST /internal/v1/cases`를 호출합니다.
4. AI가 `CREATING` 또는 `VALIDATING`이면 백엔드는 프론트에 `PREPARING`을 반환합니다.
5. AI가 `READY`이면 백엔드는 동결된 전체 `caseBlueprint`를 서버 저장소에 보관하고
   프론트에는 공개 가능한 `SessionDto`만 반환합니다.

상세 AI 시작 응답은 [`AI_BACKEND_CONTRACT.md`](./AI_BACKEND_CONTRACT.md)를 따릅니다.

상태 변환:

| AI 상태 | 프론트 상태 | 백엔드 동작 |
|---|---|---|
| `CREATING`, `VALIDATING` | `PREPARING` | `pollAfterMs`와 함께 반환 |
| `READY` | `READY` | 사건 원문과 SHA-256 저장 |
| `FAILED` | 오류 응답 | `retryable` 정책에 따라 재시도 |
| 게임 시작 후 | `IN_PROGRESS` | 백엔드 진행 상태 |
| 판정 완료 | `RESULT` | 공개 가능한 결과만 반환 |

## 공유 ID 계약

AI 서버의 다음 공개 엔드포인트에서 버전이 지정된 변환표를 조회할 수 있습니다.

```http
GET /api/v1/integration/frontend-contract
```

현재 연동 계약 버전은 `1.2.0`입니다. 사건 생성 계약은
`ARCADIA_WORLD:1.1.0`, `ARCADIA_MYSTERY_RULES:1.1.0`, 프롬프트
`case-generator-v3`를 사용합니다.

핵심 인물 변환:

| 프론트 NPC ID | 공통 인물 ID |
|---|---|
| `NPC_MAYA` | `MAYA` |
| `NPC_JUNHO` | `JUNHO` |
| `NPC_SOPHIA` | `SOPHIA` |
| `NPC_KASIM` | `KASIM` |
| `NPC_YUNA` | `YUNA` |

장소는 `ARCADIA_WORLD:1.1.0`의 다음 8개 ID를 공통 계약으로 사용합니다.

| locationId | 프론트 라벨 |
|---|---|
| `COMMANDER_OFFICE` | 사령관실 |
| `DEPUTY_COMMANDER_OFFICE` | 부사령관 집무실 |
| `CENTRAL_HUB` | 중앙 허브 |
| `MEDICAL_BAY` | 의무실 |
| `ENGINEERING_BAY` | 엔지니어링 |
| `COMMUNICATIONS_CENTER` | 통신실 |
| `CARGO_BAY` | 화물칸 |
| `COMMON_AREA` | 공용구역 |

백엔드는 이 목록을 세션의 `exploreLocationIds`로 프론트에 전달하고, 프론트는 자체
좌표·라벨 테이블에 매핑합니다. AI 서버는 생성 사건의 모든 장소 참조와 탐사 API
입력을 같은 목록으로 검증하므로 구형 ID나 임의 생성 ID는 사용할 수 없습니다.

프론트의 16개 오브젝트 ID는 화면과 수첩에서 계속 사용하며, 연동 계약 `1.2.0`에서는
모두 `EXPLORE` 모드입니다. 백엔드는 프론트가 보낸 `locationId`와 `objectHint`를 AI의
탐사 API에 전달하고, 반환된 실제 AI 단서 ID를 다음과 같이 세션별로 연결해 저장합니다.

```text
(sessionId, frontendObjectId) -> [aiClueId, ...]
```

이 연결을 브라우저가 임의로 제출한 단서 ID로 다시 만들면 안 됩니다.

### 오브젝트 단위 탐사

프론트는 오브젝트 조사 시 다음 정보를 보냅니다.

```json
{
  "locationId": "CARGO_BAY",
  "objectHint": "CG_CARGO_MANIFEST"
}
```

AI 탐사 요청의 `objectHint`는 선택 필드입니다.

- 생략하면 기존 클라이언트를 위해 `locationId`만으로 그 장소의 단서를 찾습니다.
- 제공하면 16개 정식 오브젝트 ID인지와 등록된 `locationId`가 일치하는지 검사한 뒤,
  동결된 CaseBlueprint에서 `source.sourceId == objectHint`인 단서만 반환합니다.
- 알 수 없는 ID나 장소가 맞지 않는 조합은 `400 INVALID_REQUEST`입니다.

**분리 배포된 게임 백엔드는 프론트의 `objectHint`를 AI 서버에 반드시 전달해야
합니다.** 백엔드가 AI 탐사 API 대신 저장된 CaseBlueprint에서 직접 단서를 해금한다면
동일한 `source.sourceId == objectHint` 필터를 적용해야 합니다. `locationId`만 전달하거나
장소만 필터하면 한 오브젝트 상호작용으로 같은 방의 다른 오브젝트 단서까지 동시에
공개됩니다.

사건 생성 시 모든 `EXPLORE` 단서는 `sourceType: PHYSICAL_OBJECT`, 정식 오브젝트
`sourceId`, 그 오브젝트에 등록된 `locationId`를 사용합니다. 다음 10개
`clueRequired` 오브젝트는 사건마다 각각 하나 이상의 단서를 가지며 8개 장소 전체를
커버합니다.

| locationId | 필수 단서 오브젝트 |
|---|---|
| `COMMANDER_OFFICE` | `CO_BODY`, `CO_DOOR_LOG`, `CO_TERMINAL` |
| `DEPUTY_COMMANDER_OFFICE` | `CO_XO_PASSAGE` |
| `CENTRAL_HUB` | `CO_ENV_PANEL` |
| `MEDICAL_BAY` | `MD_MEDICAL_STORAGE` |
| `ENGINEERING_BAY` | `EN_LIFE_SUPPORT` |
| `COMMUNICATIONS_CENTER` | `CM_SECURITY_ARCHIVE` |
| `CARGO_BAY` | `CG_CARGO_MANIFEST` |
| `COMMON_AREA` | `CMN_FOOD_STATION` |

허용되는 획득 방식은 `EXPLORE`, `RAG_QUERY`, `CONNECT`입니다. fallback 사건도 같은
규칙을 따르며 단서 14건(`EXPLORE` 10건 + `RAG_QUERY` 4건)을 제공합니다.

## 프론트 API별 백엔드 어댑터

| 프론트가 호출하는 API | 백엔드 처리 | AI 호출 |
|---|---|---|
| `POST /api/sessions` | 세션 생성 및 준비 상태 반환 | `POST /internal/v1/cases` |
| `GET /api/sessions/{id}` | AI 상태를 프론트 상태로 변환 | `GET /internal/v1/cases/{id}` |
| `POST .../opening/complete` | 상태·버전 저장 | 없음 |
| `POST .../objects/{objectId}/inspect` | 오브젝트와 실제 단서 연결 저장 | `explore` (`locationId` + `objectHint`) |
| `POST .../interrogations` | 심문 ID 발급 | 없음 |
| `POST /api/interrogations/{id}/messages` | NPC 별칭과 제시 단서 변환 | `interrogations/{characterId}/turns` |
| `POST .../days/{day}/complete` | 일차·버전 저장 | 없음 |
| `POST .../assistant` | 응답 필드와 인용 ID 변환 | `assistant/queries` |
| `PUT .../theory` | 낙관적 버전 검사·초안 저장 | 없음 |
| `POST .../trial/verdict` | 발견 단서만 사용해 최종 제출 | `deductions`, 이후 `result` |

## 최종 추리 필드 변환

프론트의 사건 재구성 UI는 세 필드를 사용하지만 AI 판정은 네 추리 축을 요구합니다.

| 프론트 필드 | AI 증거 역할 |
|---|---|
| `method` | `SETUP`, `TRIGGER` |
| `trace` | `OPPORTUNITY` |
| `motive` | `MOTIVE` |
| `exclusions` | 백엔드의 비범인 배제·투표 계산에 사용 |

백엔드는 선택된 프론트 오브젝트에 연결된 실제 AI 단서 중 해당 역할을 가진 단서를
찾아 `POST /api/v1/sessions/{id}/deductions`에 제출합니다. `method`에는 두 역할의
발견 단서가 모두 필요합니다. AI `caseBlueprint.solution`의 정답 ID를 브라우저에
전달하거나, 발견하지 않은 단서를 대신 제출하면 안 됩니다.

## 응답 필드 변환

수사 보조:

| AI | 프론트 |
|---|---|
| `answer` | `summary` |
| `citedRecordIds` | 공개 단서와 연결된 `citations` |
| 첫 `suggestedQueries` 항목 | `suggestedQuery` |
| AI 정적 응답 사용 여부 | `fallback` |

NPC 심문:

| AI | 프론트 |
|---|---|
| `dialogue` | `response` |
| 새로 공개되어 세션에 기록된 단서 | `revealedEvidenceIds` |

NPC 심문의 발견 단서 기준 데이터는 게임 백엔드의 `EvidenceInventory`입니다.
백엔드는 해당 NPC에게 지금까지 제시한 발견 단서 전체를 `presentedClueIds`로 보내고,
AI 서버는 각 ID가 동결된 CaseBlueprint에 속하는지만 다시 검사합니다. 따라서 백엔드가
단독으로 처리한 `EXPLORE`/`CONNECT` 단서도 별도 AI 상태 동기화 없이 제시할 수 있습니다.
CaseBlueprint에 없는 ID는 `400 INVALID_REQUEST`로 거절됩니다.

AI가 반환한 기록 ID를 그대로 프론트 `citations`에 넣으면 안 됩니다. 백엔드는 현재
세션에서 공개된 프론트 오브젝트 또는 단서 ID로 변환한 값만 반환합니다.

## 오류와 동시성

AI 서버 오류를 프론트 계약에 맞춰 다음 형태로 정규화합니다.

```json
{
  "code": "AI_CASE_GENERATION_FAILED",
  "message": "사건 생성에 실패했습니다.",
  "retryable": true
}
```

- 프론트의 `version`은 게임 백엔드가 관리합니다.
- 오래된 `version`을 사용한 저장은 `409 SESSION_VERSION_CONFLICT`로 거절합니다.
- 동일 오브젝트 조사와 심문 메시지 재전송에는 멱등 키를 적용합니다.
- 사건 생성·조회, NPC, RAG 요청에는 모두 동일한 `X-Internal-AI-Key`를 보냅니다.
- `AI_INTERNAL_API_KEY`는 게임 백엔드에만 두고 Vite 환경 변수로 만들지 않습니다.
- 운영에서는 AI 내부 API를 사설 네트워크에만 노출합니다.
