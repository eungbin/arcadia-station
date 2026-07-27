# 프런트엔드 API 연결 계약

## 전환 방법

```dotenv
VITE_API_MODE=http
VITE_API_BASE_URL=/api
VITE_API_PROXY_TARGET=http://127.0.0.1:8080
```

`mock`과 `http`는 `src/api/client.ts`의 같은 `ArcadiaApi` 인터페이스를 구현한다. 실제 연결에서는 UI나 Zustand 액션을 바꾸지 않고 HTTP 어댑터만 사용한다. 모든 요청은 JSON이며 기본 제한 시간은 10초다.

개발 서버는 `VITE_API_PROXY_TARGET`이 있을 때 `/api` 요청을 해당 게임 백엔드로
프록시한다. 운영 배포에서는 웹 서버나 게이트웨이가 동일 출처 `/api`를 백엔드로
라우팅하므로 이 값이 필요하지 않다.

## AI 서버와의 경계

브라우저는 AI 서버를 직접 호출하지 않는다.

```text
React/Vite → /api 게임 백엔드 → Arcadia AI 서버
```

게임 백엔드는 `tyoonkk/GAME_AI`의 다음 계약을 사용한다.

- 게임 시작: `POST /internal/v1/cases`
- 사건 생성 상태: `GET /internal/v1/cases/{sessionId}`
- 공유 ID 변환표: `GET /api/v1/integration/frontend-contract`
- 조사·RAG·심문·판정: AI 서버의 `/api/v1/sessions/{sessionId}/*`

백엔드는 AI의 `CREATING`과 `VALIDATING`을 프런트의 `PREPARING`으로 변환하고,
`READY`일 때 전체 사건을 서버에만 저장한다. `caseBlueprint`, 범인 ID, 미발견 단서와
`X-Internal-AI-Key`는 브라우저 응답이나 Vite 환경 변수에 포함하지 않는다.

공유 인물 ID:

| 프런트 오브젝트 | 공통 인물 ID |
|---|---|
| `NPC_MAYA` | `MAYA` |
| `NPC_JUNHO` | `JUNHO` |
| `NPC_SOPHIA` | `SOPHIA` |
| `NPC_KASIM` | `KASIM` |
| `NPC_YUNA` | `YUNA` |

오브젝트 조사 시 백엔드는 AI 변환표의 `mode`, `locationId`, `query`를 사용하고,
반환된 실제 AI 단서 ID를 `(sessionId, objectId)`에 연결해 저장한다. 프런트에는 기존
오브젝트 ID만 반환한다.

최종 이론은 다음처럼 AI의 네 증거 역할로 변환한다.

| 프런트 이론 필드 | AI 역할 |
|---|---|
| `method` | `SETUP`, `TRIGGER` |
| `trace` | `OPPORTUNITY` |
| `motive` | `MOTIVE` |
| `exclusions` | 게임 백엔드의 비범인 배제·투표 계산 |

`method` 제출에는 연결된 `SETUP`과 `TRIGGER` 발견 단서가 모두 필요하다. 백엔드는
발견하지 않은 AI 단서를 정답 데이터에서 대신 꺼내 제출하면 안 된다.

## 현재 연결 지점

| 화면 동작 | 메서드와 경로 | 프런트엔드 반영 |
|---|---|---|
| 사건 준비 | `POST /sessions`, `GET /sessions/{id}` | `PREPARING`이면 `pollAfterMs` 간격으로 최대 60초 조회 |
| 오프닝 완료 | `POST /sessions/{id}/opening/complete` | `sessionId`, `day`, `version` 저장 |
| 오브젝트 기록 | `POST /sessions/{id}/objects/{objectId}/inspect` | 공개된 증거 ID와 `version` 반영 |
| 심문 시작 | `POST /sessions/{id}/interrogations` | 심문 ID와 공개 시작 대사 표시 |
| 선택 질문·자유 질문·증거 제시 | `POST /interrogations/{id}/messages` | `choiceId`, `query`, `evidenceId` 중 하나를 전송하고 답변, 공개 증거 ID, `version` 반영 |
| 일차 종료 | `POST /sessions/{id}/days/{day}/complete` | 다음 일차와 `version` 반영 |
| 수사 보조 | `POST /sessions/{id}/assistant` | 요약, 인용 ID, 관찰, 후속 질문 표시 |
| 이론 저장 | `PUT /sessions/{id}/theory` | 현재 초안과 낙관적 잠금 `version` 제출 |
| 최종 판정 | `POST /sessions/{id}/trial/verdict` | 투표 수, 엔딩, 정오 판정과 `version` 반영 |

경로의 `/api` 접두사는 `VITE_API_BASE_URL`이 담당한다.

## 오류 응답

```json
{
  "code": "SESSION_VERSION_CONFLICT",
  "message": "다른 요청이 먼저 처리되었습니다.",
  "retryable": true
}
```

비정상 HTTP 응답은 `ArcadiaApiError`로 정규화한다. 시작·조사·일차 저장·이론·판정은 해당 화면에서 오류와 재시도 가능 상태를 유지한다. 심문과 수사 보조 AI가 실패하면 정적 응답으로 전환되어 완주를 막지 않는다.

## 보안 경계

- 브라우저에는 AI API 키, 범인 ID, 전체 사건 성경, 미발견 증거, 비공개 투표 가중치를 전달하지 않는다.
- 수사 보조 응답의 `citations`는 현재 세션에서 공개된 증거 ID만 허용한다.
- NPC 응답은 공개 가능한 답변과 새로 공개된 증거 ID만 반환한다.
- 최종 정오 판정과 표 계산은 서버가 수행한다.

## 연결 시 서버에서 확인할 항목

1. CORS 대신 동일 출처 `/api` 배포 또는 `VITE_API_PROXY_TARGET` 개발 프록시를 사용한다.
2. 중복 조사와 메시지 재전송은 멱등하게 처리한다.
3. `version` 충돌은 위 오류 형식과 `409`로 반환한다.
4. `RESULT` 세션의 변경 요청은 거부한다.
5. AI 타임아웃은 서버 정적 폴백을 우선하고, 불가능할 때만 오류를 반환한다.
