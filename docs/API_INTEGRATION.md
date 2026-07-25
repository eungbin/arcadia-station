# 프런트엔드 API 연결 계약

## 전환 방법

```dotenv
VITE_API_MODE=http
VITE_API_BASE_URL=/api
```

`mock`과 `http`는 `src/api/client.ts`의 같은 `ArcadiaApi` 인터페이스를 구현한다. 실제 연결에서는 UI나 Zustand 액션을 바꾸지 않고 HTTP 어댑터만 사용한다. 모든 요청은 JSON이며 기본 제한 시간은 10초다.

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

1. CORS 대신 동일 출처 `/api` 배포 또는 개발 프록시를 사용한다.
2. 중복 조사와 메시지 재전송은 멱등하게 처리한다.
3. `version` 충돌은 위 오류 형식과 `409`로 반환한다.
4. `RESULT` 세션의 변경 요청은 거부한다.
5. AI 타임아웃은 서버 정적 폴백을 우선하고, 불가능할 때만 오류를 반환한다.
