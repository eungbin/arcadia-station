# Gemini API 테스트 설정

이 서버는 `AI_PROVIDER=gemini`일 때 Gemini Interactions API의 구조화 출력과
Gemini Embedding API를 사용합니다. API 키는 프론트엔드가 아니라 이 AI 서버에만
설정해야 합니다.

## 1. API 키 발급

1. [Google AI Studio API Keys](https://aistudio.google.com/app/apikey)에서 로그인합니다.
2. 프로젝트를 선택하거나 새 프로젝트를 만듭니다.
3. `Create API key`로 키를 만든 뒤 복사합니다.

새로 발급한 인증 키를 사용하고, 키를 Git이나 채팅에 올리지 마세요.

## 2. PowerShell에서 실행

현재 PowerShell 창에서만 유효한 환경변수를 설정합니다.

```powershell
$env:AI_PROVIDER = 'gemini'
$env:AI_OFFLINE_MODE = 'false'
$env:AI_ENABLED = 'true'
$env:GEMINI_API_KEY = '<Google AI Studio에서 복사한 키>'
$env:GEMINI_TEXT_MODEL = 'gemini-3.6-flash'
$env:GEMINI_EMBEDDING_MODEL = 'gemini-embedding-2'
$env:AI_CASE_GENERATION_TIMEOUT = '60s'
mvn spring-boot:run
```

`mvn` 명령이 PATH에 없다면 프로젝트에서 사용 중인 Maven 실행 파일의 전체 경로로
실행해도 됩니다. 서버가 시작되면 다음 명령으로 상태를 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

그 다음 사건 생성 API를 호출하면 Gemini가 사건 초안을 생성하고, 서버의 JSON Schema
및 게임 규칙 검증을 통과한 결과만 게임 데이터로 고정합니다.

```powershell
$body = @{ seed = 'gemini-smoke-test' } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/sessions `
  -ContentType 'application/json' `
  -Body $body
```

## 3. 키 제거

테스트가 끝난 뒤 현재 PowerShell 세션에서 키를 제거할 수 있습니다.

```powershell
Remove-Item Env:GEMINI_API_KEY
```

`.env` 파일은 Git에서 제외되어 있지만, 현재 서버는 PowerShell 환경변수를 직접 읽는
방식을 기준으로 합니다. `application.yml`, Java 소스, 프론트엔드의 `VITE_*` 환경변수에는
API 키를 넣지 마세요.

## 설정값

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `AI_PROVIDER` | `openai` | `gemini`으로 설정하면 Gemini 사용 |
| `AI_OFFLINE_MODE` | `true` | 실제 API 호출 시 `false` |
| `GEMINI_API_KEY` | 없음 | Google AI Studio에서 발급한 비밀 키 |
| `GEMINI_TEXT_MODEL` | `gemini-3.6-flash` | 사건/NPC/RAG 구조화 출력 모델 |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-2` | 증거 검색 임베딩 모델 |
| `GEMINI_BASE_URL` | Google Generative Language v1beta | 테스트 프록시가 있을 때만 변경 |
| `AI_QUOTA_COOLDOWN` | `10m` | HTTP 429/쿼터 초과 후 외부 API 호출을 막는 시간 |
| `AI_CASE_GENERATION_TIMEOUT` | `60s` | 큰 사건 JSON 구조화 출력 제한 시간 |
| `AI_CASE_GENERATION_MAX_RETRIES` | `2` | 최초 시도 이후 추가 재시도 횟수 |

## 쿼터 초과 시 동작

Gemini가 HTTP 429 또는 `RESOURCE_EXHAUSTED`를 반환하면 AI 서버는 기본 10분 동안
Gemini 호출 회로를 차단합니다. 이 시간에는 외부 API를 다시 호출하지 않고 다음 방식으로
게임을 계속 진행합니다.

- 사건 생성: 서버에 포함된 검증 완료 사건으로 즉시 전환
- NPC 심문: 허용된 사실만 사용하는 결정적 응답으로 전환
- RAG 요약: 검색된 기록을 서버 코드로 조합
- 임베딩 검색: 임베딩 없이 키워드 검색 사용

쿨다운 시간은 `AI_QUOTA_COOLDOWN` 환경변수로 바꿀 수 있습니다.
