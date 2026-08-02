# Arcadia Station AI

Gemini API로 테스트하려면 [Gemini API 테스트 설정](docs/GEMINI_SETUP.md)을 따르세요.

아르카디아 스테이션의 세션별 추리 사건을 생성하고, 서버 규칙으로 검증·동결한 뒤
탐색/RAG/심문/최종 추리 API를 제공하는 Spring Boot 서비스입니다.

핵심 원칙은 다음과 같습니다.

- AI는 세션 시작 시 사건 전체 `CaseBlueprint`를 한 번 생성합니다.
- 세계관, 인물 권한, 장소, 시스템과 추리 공정성 규칙은 버전 리소스로 고정합니다.
- Structured Outputs 성공 여부와 별개로 서버 검증기가 참조, 권한, 타임라인,
  단서 그래프, 필수 추리 축과 유일 범인을 판정합니다.
- 검증을 통과한 사건은 SHA-256과 함께 동결하며 세션 중 수정하지 않습니다.
- RAG는 동결 사건의 확정 기록만 검색하고, NPC에는 허용된 사실만 전달합니다.
- 최종 정답은 LLM이 아니라 발견한 단서 ID를 서버 코드로 판정합니다.

## 요구 환경

- JDK 21
- Maven은 따로 설치하지 않아도 됩니다. 저장소에 포함된 래퍼(`mvnw`, `mvnw.cmd`)가
  Maven 3.9.9를 자동으로 내려받아 사용합니다.

## 로컬 실행

기본값은 API 키 없이 완주 가능한 오프라인 모드입니다.
게임 백엔드와 함께 실행하는 전체 절차는
[AI 서버 로컬 연동 테스트](docs/AI_SERVER_LOCAL_TEST.md)를 따르세요.

모노레포 루트가 아니라 `ai-server/` 디렉터리에서 실행합니다.

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Git Bash, macOS, Linux에서는 `./mvnw`를 사용합니다.

패키징한 jar로 실행하려면 먼저 `.\mvnw.cmd package`로 빌드해야 합니다.
`target/`은 버전 관리 대상이 아니라 새로 클론한 직후에는 비어 있습니다.

```powershell
.\mvnw.cmd package
java -jar target/station-ai-0.1.0-SNAPSHOT.jar
```

헬스 체크:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

온라인 사건 생성을 사용할 때만 다음 환경 변수를 설정합니다.

```powershell
$env:AI_OFFLINE_MODE = 'false'
$env:AI_ENABLED = 'true'
$env:OPENAI_API_KEY = '<secret>'
$env:OPENAI_TEXT_MODEL = 'gpt-5.6-terra'
$env:OPENAI_EMBEDDING_MODEL = 'text-embedding-3-small'
.\mvnw.cmd spring-boot:run
```

환경변수 전체 목록과 Gemini/오프라인 실행 예시는 [`.env.example`](.env.example)에
정리되어 있습니다. Spring Boot가 `.env` 파일을 자동으로 읽는 구성은 아니므로,
실제 실행 시에는 문서 예시처럼 현재 셸이나 배포 환경의 환경변수로 주입해야 합니다.

`OPENAI_API_KEY`와 원본 프롬프트는 응답이나 일반 로그에 기록하지 않습니다. 모델명은
환경 변수로 교체할 수 있으며 코드에 고정하지 않습니다.

## 게임 수직 절단

1. `POST /api/v1/sessions`로 세션 생성
2. `GET /api/v1/sessions/{id}/status` 폴링
3. `GET /api/v1/sessions/{id}`로 브리핑·공개 상태 조회
4. `POST /api/v1/sessions/{id}/explore`로 물리 단서 발견
5. `POST /api/v1/sessions/{id}/assistant/queries`로 확정 기록 검색
6. `POST /api/v1/sessions/{id}/interrogations/SOPHIA/turns`로 증거 제시 심문
7. `POST /api/v1/sessions/{id}/deductions`로 네 추리 축 제출

탐사 `locationId`는 `ARCADIA_WORLD:1.1.0`의 8개 고정 로스터
(`COMMANDER_OFFICE`, `DEPUTY_COMMANDER_OFFICE`, `CENTRAL_HUB`, `MEDICAL_BAY`,
`ENGINEERING_BAY`, `COMMUNICATIONS_CENTER`, `CARGO_BAY`, `COMMON_AREA`)만
사용합니다.
8. `GET /api/v1/sessions/{id}/result`로 사건 재구성 조회

프론트용 `GET /api/v1/sessions/{id}` 응답에는 범인, 진실 요약, 전체 해답,
미발견 단서, NPC의 숨긴 사실이 포함되지 않습니다.

백엔드가 별도 서비스라면 [AI-백엔드 연동 계약](docs/AI_BACKEND_CONTRACT.md)의
`/internal/v1/cases`와 NPC/RAG API를 사용하세요. `AI_INTERNAL_API_KEY`가 설정된
환경에서는 사건 생성·조회, NPC 심문, RAG 검색에 같은 `X-Internal-AI-Key` 헤더가
필수입니다. 운영 환경에서는 이 경로들을 사설 네트워크에만 노출해야 합니다.
백엔드 DTO 작성용 축약 없는 실제 `READY` 응답은
[`docs/examples/internal-case-ready.response.json`](docs/examples/internal-case-ready.response.json)에
있으며 통합 테스트가 현재 HTTP 응답과의 일치 여부를 검사합니다.

`eungbin/arcadia-station` 프론트와 게임 백엔드까지 연결할 때는
[프론트-백엔드-AI 브리지 계약](docs/FRONTEND_BACKEND_AI_BRIDGE.md)을 사용하세요.
공유 NPC·오브젝트·추리 필드 변환표는
`GET /api/v1/integration/frontend-contract`에서 버전이 지정된 JSON으로 조회할 수
있습니다. 브라우저가 AI 서버의 내부 사건 생성 API를 직접 호출하는 구조는 지원하지
않습니다.

## 주요 리소스

- `src/main/resources/ai/world/arcadia-world-v1.json`
- `src/main/resources/ai/rules/arcadia-mystery-rules-v1.json`
- `src/main/resources/ai/fallback/sophia-safe-v1.json`
- `src/main/resources/ai/schema/`
- `src/main/resources/integration/frontend-contract-v1.json`

## 현재 저장 방식

MVP는 세션과 동결 사건을 인메모리 저장소에 보관합니다. 프로세스 재시작 시 세션이
사라지므로 운영 연결 시 `FrozenCaseBlueprint`와 증거 인벤토리를 DB 저장소로
교체해야 합니다. 도메인 서비스는 저장소 구현과 분리되어 있습니다.
