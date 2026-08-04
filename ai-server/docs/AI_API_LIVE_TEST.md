# AI 외부 API 호출·단서 로그 확인 가이드

이 문서는 다음 세 가지를 서로 구분해서 확인하는 절차입니다.

1. 테스트 더블로 API 코드 경로만 검증
2. AI 서버를 직접 호출해 fallback 동작 검증
3. 실제 Gemini/OpenAI를 호출해 사건과 단서가 생성됐는지 검증

`READY`만으로는 실제 AI 성공을 뜻하지 않습니다. 게임을 계속 진행할 수 있도록 외부 API가
실패해도 fallback 사건을 `READY`로 반환하기 때문입니다. 실제 성공 조건은 반드시 다음 두
로그가 함께 있는 것입니다.

```text
[AI-API][SUCCESS] ... purpose=CASE_GENERATION ... httpStatus=200
[AI-CASE][RESULT] ... mode=API generationSource=AI fallbackReason=NONE
```

## 1. 사전 준비

- JDK 21 설치 후 새 PowerShell에서 `java -version` 확인
- 조직 저장소 최신 `master` pull
- PowerShell 작업 위치를 `arcadia-station/ai-server`로 이동
- 실제 API 테스트에는 해당 공급자의 유효한 API 키 필요

API 키, `.env`, IDE Run Configuration은 컴퓨터별 비밀 설정입니다. Git에 커밋하지 않습니다.

## 2. 비용 없는 읽기 쉬운 로그 테스트

```powershell
cd <arcadia-station 경로>\ai-server
.\scripts\show-case-generation-logs.cmd
```

이 스크립트는 Maven/Spring 전체 출력에서 아래 항목만 골라 보여줍니다.

- 선택된 모드: API 또는 FALLBACK
- 세션 요청 도착 여부
- 사건 생성 결과와 fallback 사유
- 생성된 단서 14개 전체

여기서 `API`는 외부 비용과 비결정성을 피하기 위한 provider test double입니다. 실제 Gemini
연결 증명은 다음 절차를 사용합니다.

## 3. AI 서버 직접 fallback 스모크 테스트

```powershell
.\scripts\test-ai-case-generation.cmd -Mode fallback
```

스크립트가 JAR를 빌드하고 임시 AI 서버를 8081에서 시작한 뒤 실제
`POST /internal/v1/cases`를 호출합니다. 완료 후 서버를 자동 종료하고 단서 목록을 읽기 쉽게
출력합니다. 정상 결과는 다음과 같습니다.

```text
generationSource=FALLBACK
fallbackReason=OFFLINE_MODE
PASS: 외부 호출 없이 FALLBACK 사건과 단서 로그를 확인했습니다.
```

8081이 사용 중이면 기존 서버를 종료하거나 `-Port 18081`처럼 다른 포트를 지정합니다.

## 4. 실제 Gemini 호출 스모크 테스트

```powershell
.\scripts\test-ai-case-generation.cmd -Mode gemini
```

`GEMINI_API_KEY`가 현재 PowerShell 환경에 없으면 스크립트가 키를 숨김 입력으로 요청합니다.
키는 화면·명령 기록·로그에 출력하지 않으며, 스크립트 종료 후 프로세스 환경도 원래 값으로
복구합니다. 실제 공급자 검증이 로컬 test double이나 프록시로 바뀌지 않도록 이 스크립트는
실행 중 `GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta`를 강제로 사용하고
종료 후 기존 값을 복구합니다.

성공 판정:

```text
[AI-MODE] ... configuredMode=API selectedGateway=GEMINI ...
[AI-API][REQUEST] ... provider=gemini purpose=CASE_GENERATION ...
[AI-API][SUCCESS] ... httpStatus=200 ...
[AI-CASE][RESULT] ... mode=API generationSource=AI fallbackReason=NONE ...
PASS: 실제 GEMINI API 호출과 AI 단서 생성을 확인했습니다.
```

OpenAI를 사용할 때는 다음과 같습니다.

```powershell
.\scripts\test-ai-case-generation.cmd -Mode openai
```

OpenAI 모드도 실행 중에는 공식 `https://api.openai.com/v1` URL만 사용합니다.

## 5. 실패 로그 읽는 법

외부 요청 실패 시 키·프롬프트·응답 본문 대신 안전한 분류만 기록합니다.

| 로그 | 의미 | 우선 확인할 것 |
|---|---|---|
| `400 INVALID_REQUEST` | 모델 요청 또는 schema 거부 | 모델명, schema 지원 범위 |
| `400/401 AUTHENTICATION` | 키 인증 실패 | 선택한 provider와 키가 맞는지 |
| `403 PERMISSION` | 프로젝트/API 권한 부족 | API 활성화와 프로젝트 권한 |
| `404 MODEL_OR_ENDPOINT_NOT_FOUND` | 모델 또는 base URL 오류 | 모델명과 base URL override |
| `429 RATE_LIMITED` | quota/rate limit | 할당량과 결제 상태 |
| `TIMEOUT` | 응답 시간 초과 | 네트워크와 timeout |
| `NETWORK` | DNS·연결·TLS 문제 | 방화벽·프록시·인터넷 |
| `INVALID_RESPONSE` | HTTP 성공 후 JSON/schema 해석 실패 | 공급자 응답과 내부 계약 차이 |

스크립트는 원본 로그 경로도 마지막에 보여줍니다. 실제 키나 공급자 원문 오류 응답은 로그에
남기지 않습니다.

## 6. 프론트 → 백엔드 → AI 실제 연결

직접 AI 스모크가 성공한 다음 세 프로세스를 각각 실행합니다.

AI 서버 PowerShell:

```powershell
cd <arcadia-station 경로>\ai-server
$env:PORT='8081'
$env:AI_INTERNAL_API_KEY='arcadia-local-shared-key'
$env:AI_ENABLED='true'
$env:AI_OFFLINE_MODE='false'
$env:AI_PROVIDER='gemini'
$env:GEMINI_API_KEY='<개인 키>'
$env:AI_CASE_GENERATION_MAX_RETRIES='0'
.\mvnw.cmd spring-boot:run
```

백엔드 PowerShell/Docker:

```powershell
cd <arcadia-station 경로>\backend
docker compose `
  -f docker-compose.yml `
  -f compose.real-ai.yml `
  up -d --build --force-recreate
```

기본 `docker compose up`만 실행하면 고정 Fake 픽스처를 사용하므로 단서가 반복됩니다.

프론트 PowerShell:

```powershell
cd <arcadia-station 경로>\frontend
$env:VITE_API_MODE='http'
$env:VITE_API_BASE_URL='/api'
$env:VITE_API_PROXY_TARGET='http://127.0.0.1:8080'
npm.cmd run dev
```

브라우저 Network에 `POST /api/v1/sessions`가 보여야 합니다. 세션 ID가 `LOCAL-*`이면
프론트 mock이므로 백엔드와 AI를 전혀 호출하지 않은 것입니다. AI 콘솔에는
`[GAME-SESSION][START]`와 최종 `[AI-CASE][RESULT] mode=API`가 모두 보여야 합니다.

## 7. 왜 단서가 세 번 모두 같았는가

다음 중 하나면 고정 데이터이므로 같은 단서가 반복되는 것이 정상입니다.

- 프론트 기본 `VITE_API_MODE=mock`
- 백엔드 기본 `!real-ai` 프로파일의 Fake 픽스처
- AI 서버 기본 `AI_OFFLINE_MODE=true`
- API 키 누락·quota·provider 오류 후 fallback 전환

최종 로그의 `clueSetSha256`가 같고 `generationSource=FALLBACK`이면 고정 fallback을 사용한
것입니다. `generationSource=AI`인데도 반복될 때만 실제 모델 생성 다양성 문제로 분리해
조사합니다.

## 공식 계약 참고

- [Gemini Interactions API](https://ai.google.dev/api/interactions-api-v1)
- [Gemini structured outputs](https://ai.google.dev/gemini-api/docs/structured-output?lang=rest)
- [Gemini models](https://ai.google.dev/gemini-api/docs/models)
