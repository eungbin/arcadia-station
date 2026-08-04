# AI 서버 로컬 연동 테스트

> 대상: 모노레포 `hackathon-yaho/arcadia-station`의 `ai-server/`와 `backend/`
> 기본 주소: AI `http://127.0.0.1:8081`, 백엔드 `http://127.0.0.1:8080`

## 1. 준비 사항

- AI 서버: JDK 21 (Maven은 `ai-server/`의 `mvnw` 래퍼가 자동으로 내려받으므로 설치 불필요)
- 백엔드: `backend/`의 Gradle Wrapper와 Docker
- 두 서버에 동일한 `AI_INTERNAL_API_KEY`
- 포트 8080/8081이 사용 중이 아닌지 확인

실제 API 키는 Git, Markdown, 메신저 공개 채널에 올리지 않습니다. 로컬 연동을 먼저
확인할 때는 Gemini 키가 필요 없는 오프라인 모드를 권장합니다.

`.env.example`은 변수 목록을 보여주는 참고 파일입니다. 현재 Spring Boot 구성은
`.env`를 자동으로 읽지 않으므로, 아래처럼 PowerShell 프로세스 환경변수로 설정합니다.

## 2. AI 서버 실행: 오프라인 모드

새 PowerShell 창에서:

```powershell
cd <arcadia-station 클론 경로>\ai-server

$env:PORT = '8081'
$env:AI_INTERNAL_API_KEY = 'arcadia-local-shared-key'
$env:AI_ENABLED = 'true'
$env:AI_OFFLINE_MODE = 'true'

.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

오프라인 모드는 외부 API를 호출하지 않습니다.

- 사건 생성: 검증된 내장 fallback 사건
- NPC 심문: 허용된 사실 기반 결정론적 응답
- RAG: 키워드 검색과 결정론적 요약

헬스 체크:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

정상 응답:

```json
{"status":"UP"}
```

## 3. AI 사건 생성 단독 확인

AI 서버가 실행 중인 다른 PowerShell 창에서:

```powershell
$aiKey = 'arcadia-local-shared-key'
$headers = @{ 'X-Internal-AI-Key' = $aiKey }
$request = @{
  sessionId = 'localtest01'
  seed = 'backend-local-seed'
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri 'http://127.0.0.1:8081/internal/v1/cases' `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body $request
```

상태 확인:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri 'http://127.0.0.1:8081/internal/v1/cases/localtest01' `
  -Headers $headers
```

오프라인 모드에서는 짧은 시간 안에 다음 상태가 됩니다.

```text
status=READY
generation.generationSource=FALLBACK
errorCode=null
```

`FALLBACK + READY`는 실패가 아니라 정상 플레이 가능한 사건입니다.

## 4. 백엔드를 real-ai 프로파일로 실행

AI 서버를 종료하지 않은 상태에서 새 PowerShell 창을 엽니다.

```powershell
cd <arcadia-station 클론 경로>\backend

docker compose `
  -f docker-compose.yml `
  -f compose.real-ai.yml `
  up -d --build
```

`compose.real-ai.yml`은 Compose가 자동으로 읽는 이름이 아니므로 위처럼 `-f`로 명시해야
합니다. 이 파일이 `real-ai` 프로파일과 `AI_SERVER_BASE_URL`, `AI_INTERNAL_API_KEY`를
함께 주입하므로 환경변수를 따로 설정하지 않습니다. 컨테이너에서 호스트의 AI 서버로
나가는 경로도 `host.docker.internal`로 이 파일에 설정되어 있습니다.

IDE에서 백엔드를 직접 디버깅하려면 DB만 띄우는 방법을 `backend/README.md`의
"로컬 개발" 항목에서 확인하세요. 기본 compose는 `app`도 8080에 띄우므로
`gradlew bootRun`을 함께 실행하면 포트가 충돌합니다.

두 서버의 `AI_INTERNAL_API_KEY` 값은 반드시 같아야 합니다. 프론트나 브라우저에는 이
키를 전달하지 않습니다.

백엔드는 포트 8080, AI 서버는 포트 8081을 사용하므로 다음 경로가 서로 다른 서버를
가리키는지 확인합니다.

```text
백엔드: http://127.0.0.1:8080/actuator/health
AI:     http://127.0.0.1:8081/actuator/health
```

## 5. Gemini 온라인 모드

오프라인 전체 연동이 먼저 성공한 후 AI 서버를 다시 실행합니다.

```powershell
cd <arcadia-station 클론 경로>\ai-server

$env:PORT = '8081'
$env:AI_INTERNAL_API_KEY = 'arcadia-local-shared-key'
$env:AI_ENABLED = 'true'
$env:AI_OFFLINE_MODE = 'false'
$env:AI_PROVIDER = 'gemini'
$env:GEMINI_API_KEY = '<개인 Gemini API 키>'

# 최초 smoke test에서는 긴 재시도를 피하기 위해 0을 권장
$env:AI_CASE_GENERATION_MAX_RETRIES = '0'
$env:AI_CASE_GENERATION_TIMEOUT = '60s'

.\mvnw.cmd spring-boot:run
```

모델을 명시해야 할 때만 다음 변수를 추가합니다.

```powershell
$env:GEMINI_TEXT_MODEL = '<사용 가능한 Gemini text model>'
$env:GEMINI_EMBEDDING_MODEL = '<사용 가능한 Gemini embedding model>'
```

API quota 초과, timeout, JSON 변환 실패 또는 게임 규칙 검증 실패가 발생하면 AI 서버는
가능한 경우 내장 fallback 사건으로 전환합니다. 이 경우에도 응답은
`status=READY`, `generationSource=FALLBACK`입니다.

## 6. 로그로 실제 AI 생성 여부 확인

AI 서버 시작 시 선택된 실행 경로가 한 줄로 출력됩니다.

```text
event=ai_runtime_configured selectedGateway=FALLBACK externalAiEnabled=false fallbackReason=OFFLINE_MODE ... repeatedClueSetExpected=true
```

실제 공급자가 선택되면 `selectedGateway=GEMINI`, `externalAiEnabled=true`,
`fallbackReason=NONE`으로 표시됩니다. API 키 원문은 로그에 출력하지 않고 설정 여부만
`apiKeyConfigured=true|false`로 표시합니다.

세션 요청이 AI 서버에 도착하면 다음 이벤트가 순서대로 출력됩니다.

```text
event=session_case_accepted sessionId=...
event=case_generation_started sessionId=... externalAiEnabled=true
event=case_generation_completed sessionId=... generationSource=AI fallbackReason=NONE ...
event=case_generation_clues sessionId=... clueSetSha256=... clues=[...]
```

`case_generation_completed`에는 생성 출처, fallback 사유, 외부 AI 생성 경로 진입 여부,
시도 횟수,
전체 청사진 해시, 단서 집합 전용 `clueSetSha256`, 단서 개수가 기록됩니다.
`case_generation_clues`에는 정답·전체 단서 본문 대신 단서 ID·제목·장소·오브젝트와 획득
방식만 안전한 JSON 한 줄로 기록됩니다. 비정상적으로 큰 응답이 로그를 과도하게 차지하지
않도록 상세 목록은 최대 50개까지만 기록하며 전체 단서 수와 지문은 그대로 남깁니다.

서로 다른 세션에서 `clueSetSha256`가 같고 `generationSource=FALLBACK`이면 동일한 내장
fallback 단서가 사용된 정상 동작입니다. fallback은 세션 ID와 seed만 바꾸고 단서 내용은
고정합니다. 대표 fallback 사유는 다음과 같습니다.

- `AI_DISABLED`: `AI_ENABLED=false`
- `OFFLINE_MODE`: `AI_OFFLINE_MODE=true`
- `MISSING_API_KEY`: 선택한 공급자의 키가 없음
- `QUOTA_EXCEEDED`: 429 또는 quota cooldown
- `GENERATION_FAILURE`: 프롬프트 구성·공급자 호출·응답 변환 등 생성 경로 실패
- `VALIDATION_EXHAUSTED`: 모든 AI 생성 시도가 게임 규칙 검증에 실패

프론트에서 세션을 시작했는데 AI 콘솔에 `session_case_accepted`가 전혀 없다면 요청이 AI
서버까지 오지 않은 것입니다. 프론트가 HTTP 모드인지, 백엔드가 `real-ai` 프로파일로
실행됐는지 확인합니다. 실제 AI 생성은 아래 조건이 모두 필요합니다.

```text
VITE_API_MODE=http
SPRING_PROFILES_ACTIVE=real-ai
AI_ENABLED=true
AI_OFFLINE_MODE=false
AI_PROVIDER=gemini
GEMINI_API_KEY=<유효한 키>
```

## 7. 현재 로컬 테스트 범위와 제약

- 사건 생성·조회, NPC, RAG는 동일한 `X-Internal-AI-Key`를 검사합니다.
- AI 세션과 RAG 인덱스는 메모리 기반이라 AI 서버 재시작 시 사라집니다.
- 사건 생성 때 사용한 `aiCaseRequestId`를 이후 NPC/RAG에도 동일하게 사용해야 합니다.
- NPC의 `presentedClueIds`는 해당 NPC에게 지금까지 제시한 누적 단서 전체를 보냅니다.
- 백엔드가 EXPLORE/CONNECT로 발견한 단서도 `presentedClueIds`로 제시할 수 있습니다.
  AI 서버는 동결된 CaseBlueprint에 존재하는 ID인지 확인하고, 발견 여부는 백엔드의
  `EvidenceInventory` 검증을 신뢰합니다.
- 생성 스키마와 프롬프트는 백엔드가 지원하는 `EXPLORE`, `RAG_QUERY`, `CONNECT` 획득
  타입만 허용합니다.

## 8. 백엔드 개발자에게 전달할 설정

```text
AI_SERVER_BASE_URL=http://127.0.0.1:8081
AI_INTERNAL_API_KEY=<AI 서버와 동일한 로컬 공유 키>
SPRING_PROFILES_ACTIVE=real-ai
```

AI 서버 측 최소 설정:

```text
PORT=8081
AI_INTERNAL_API_KEY=<백엔드와 동일한 로컬 공유 키>
AI_ENABLED=true
AI_OFFLINE_MODE=true
```

먼저 오프라인 모드로 전체 연동을 확인한 뒤, AI 서버에서만 `AI_OFFLINE_MODE=false`와
`GEMINI_API_KEY`를 설정해 온라인 테스트로 전환합니다.
