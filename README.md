# Arcadia Station — 로컬 실제 Gemini 연동

이 저장소는 프론트엔드(`frontend/`), 게임 백엔드(`backend/`), AI 서버(`ai-server/`)로
구성됩니다. 실제 Gemini 호출은 AI 서버만 수행하며, API 키는 절대로 Git이나 채팅에 올리지
않습니다.

## 1. AI 서버만 실제 Gemini로 실행

처음 한 번과 이후 실행 모두 아래 한 명령으로 시작합니다.

```powershell
cd <arcadia-station 경로>\ai-server
.\scripts\run-real-ai.cmd
```

첫 실행이면 화면에 보이지 않는 입력으로 Gemini API 키를 한 번 요청하고,
`ai-server/.env` 파일을 자동으로 만듭니다. 이후에는 같은 명령만 실행하면 됩니다.

`.env`는 `KEY=VALUE` 형태의 **내 컴퓨터 전용 설정 파일**입니다. 이 파일은
`ai-server/.gitignore`에 의해 Git에서 무시되므로 API 키를 로컬에만 보관하는 용도입니다.
키를 교체하려면 `ai-server/.env`의 `GEMINI_API_KEY=` 뒤 값만 바꾸면 됩니다.

서버가 시작된 터미널에는 다음처럼 실제 API 모드가 표시되어야 합니다.

```text
[AI-MODE] ... configuredMode=API selectedGateway=GEMINI externalAiEnabled=true ...
```

## 2. AI 서버 단독 실API 테스트

실제 Gemini로 사건과 단서를 생성하고, 결과를 검증한 뒤 서버를 자동 종료합니다.
AI 서버가 이미 8081에서 실행 중이면 먼저 `Ctrl+C`로 종료합니다.

```powershell
cd <arcadia-station 경로>\ai-server
.\scripts\test-ai-case-generation.cmd -Mode gemini
```

`.env`가 있으면 키 입력 없이 사용하며, 없으면 숨김 입력으로 키를 요청합니다.
성공 기준은 다음 세 줄입니다.

```text
[AI-API][SUCCESS] ... purpose=CASE_GENERATION ... httpStatus=200
[AI-CASE][RESULT] ... mode=API generationSource=AI fallbackReason=NONE
PASS: 실제 GEMINI API 호출과 AI 단서 생성을 확인했습니다.
```

## 3. 프론트엔드·백엔드·AI 전체 테스트

세 터미널을 따로 열어 아래 순서로 실행합니다. Docker Desktop이 먼저 실행되어 있어야 합니다.

### 터미널 A — AI 서버

```powershell
cd <arcadia-station 경로>\ai-server
.\scripts\run-real-ai.cmd
```

### 터미널 B — 백엔드 real-ai 프로필

```powershell
cd <arcadia-station 경로>\backend
docker compose `
  -f docker-compose.yml `
  -f compose.real-ai.yml `
  up -d --build --force-recreate
```

### 터미널 C — 프론트엔드 HTTP 모드

```powershell
cd <arcadia-station 경로>\frontend
npm.cmd install

$env:VITE_API_MODE='http'
$env:VITE_API_BASE_URL='/api'
$env:VITE_API_PROXY_TARGET='http://127.0.0.1:8080'

npm.cmd run dev
```

브라우저 주소는 프론트 터미널에 표시됩니다(보통 `http://127.0.0.1:5173`).
`LOCAL-*` 세션 ID가 보이면 프론트 mock 모드이므로 터미널 C의 `VITE_API_MODE=http` 설정을
확인합니다.

## 4. 실제 Gemini 호출 확인법

AI 서버를 실행한 **터미널 A**의 로그가 기준입니다.

| 플레이 동작 | 호출 여부 | 성공 로그 |
|---|---|---|
| 새 게임 시작 | Gemini 1회 | `purpose=CASE_GENERATION` + `httpStatus=200` |
| 오브젝트 조사 | Gemini 호출 없음 | 기존 사건의 동결 단서 반환 |
| NPC 창 처음 열기 | Gemini 호출 없음 | 프론트의 정적 첫 대사 |
| 추천 질문·직접 질문·단서 제시 | 질문마다 Gemini 1회 | `purpose=NPC_TURN` + `httpStatus=200` |

질문이 실제 Gemini 응답으로 처리됐는지는 아래 두 로그가 연속으로 나오면 확인됩니다.

```text
[AI-API][REQUEST] ... purpose=NPC_TURN ...
[AI-API][SUCCESS] ... purpose=NPC_TURN ... httpStatus=200
```

`[AI-CASE][RESULT] ... mode=FALLBACK` 또는 `[AI-API][FAILURE]`가 보이면 해당 결과는
실제 AI 생성 결과를 사용하지 못하고 안전한 fallback으로 전환된 것입니다.

## 5. 종료

- AI 서버: 터미널 A에서 `Ctrl+C`
- 백엔드: `cd backend` 후 위와 동일한 `-f` 옵션을 사용해 `docker compose down`
- 프론트엔드: 터미널 C에서 `Ctrl+C`

세부 AI 계약과 API 형식은 [ai-server/README.md](ai-server/README.md),
[backend/README.md](backend/README.md), [frontend/README.md](frontend/README.md)를 참고합니다.
