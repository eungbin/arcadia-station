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

docker compose up -d

$env:SPRING_PROFILES_ACTIVE = 'real-ai'
$env:AI_SERVER_BASE_URL = 'http://127.0.0.1:8081'
$env:AI_INTERNAL_API_KEY = 'arcadia-local-shared-key'

.\gradlew.bat bootRun
```

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

## 6. 현재 로컬 테스트 범위와 제약

- 사건 생성·조회, NPC, RAG는 동일한 `X-Internal-AI-Key`를 검사합니다.
- AI 세션과 RAG 인덱스는 메모리 기반이라 AI 서버 재시작 시 사라집니다.
- 사건 생성 때 사용한 `aiCaseRequestId`를 이후 NPC/RAG에도 동일하게 사용해야 합니다.
- NPC의 `presentedClueIds`는 해당 NPC에게 지금까지 제시한 누적 단서 전체를 보냅니다.
- 백엔드가 EXPLORE/CONNECT로 발견한 단서도 `presentedClueIds`로 제시할 수 있습니다.
  AI 서버는 동결된 CaseBlueprint에 존재하는 ID인지 확인하고, 발견 여부는 백엔드의
  `EvidenceInventory` 검증을 신뢰합니다.
- staging 생성에서는 `INTERROGATE`와 `AUTO` 획득 타입을 배제하는 AI 수정이 남아 있습니다.

위 제약은 로컬 단일 프로세스 연동 확인에는 영향을 주지 않지만, staging/production
배포 전에는 P0 작업으로 해결해야 합니다.

## 7. 백엔드 개발자에게 전달할 설정

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
