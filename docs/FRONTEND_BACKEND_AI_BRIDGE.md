# 프론트 ↔ 게임 백엔드 ↔ AI 서버 연동

대상 저장소:

- 프론트: `eungbin/arcadia-station`
- AI 서버: `tyoonkk/GAME_AI`

두 저장소는 서로를 Git remote로 합치지 않습니다. 브라우저가 AI 서버를 직접 호출하지
않고 게임 백엔드가 두 계약을 변환하는 구조로 연결합니다.

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
mvn spring-boot:run
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

핵심 인물 변환:

| 프론트 NPC ID | 공통 인물 ID |
|---|---|
| `NPC_MAYA` | `MAYA` |
| `NPC_JUNHO` | `JUNHO` |
| `NPC_SOPHIA` | `SOPHIA` |
| `NPC_KASIM` | `KASIM` |
| `NPC_YUNA` | `YUNA` |

프론트 오브젝트 ID는 화면과 수첩에서 계속 사용합니다. 백엔드는 변환표의 `mode`,
`locationId`, `query`를 사용해 AI의 탐색 또는 RAG API를 호출하고, 반환된 실제
AI 단서 ID를 다음과 같이 세션별로 연결해 저장합니다.

```text
(sessionId, frontendObjectId) -> [aiClueId, ...]
```

이 연결을 브라우저가 임의로 제출한 단서 ID로 다시 만들면 안 됩니다.

## 프론트 API별 백엔드 어댑터

| 프론트가 호출하는 API | 백엔드 처리 | AI 호출 |
|---|---|---|
| `POST /api/sessions` | 세션 생성 및 준비 상태 반환 | `POST /internal/v1/cases` |
| `GET /api/sessions/{id}` | AI 상태를 프론트 상태로 변환 | `GET /internal/v1/cases/{id}` |
| `POST .../opening/complete` | 상태·버전 저장 | 없음 |
| `POST .../objects/{objectId}/inspect` | 오브젝트와 실제 단서 연결 저장 | `explore` 또는 `assistant/queries` |
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
- `AI_INTERNAL_API_KEY`는 게임 백엔드에만 두고 Vite 환경 변수로 만들지 않습니다.
- 운영에서는 AI 내부 API를 사설 네트워크에만 노출합니다.
