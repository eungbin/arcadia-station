# Arcadia Station AI

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
- Maven 3.9+

## 로컬 실행

기본값은 API 키 없이 완주 가능한 오프라인 모드입니다.

```powershell
mvn test
mvn spring-boot:run
```

헬스 체크:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

온라인 사건 생성을 사용할 때만 다음 환경 변수를 설정합니다.

```powershell
$env:AI_OFFLINE_MODE = 'false'
$env:AI_ENABLED = 'true'
$env:OPENAI_API_KEY = '<secret>'
$env:OPENAI_TEXT_MODEL = 'gpt-5.6-terra'
$env:OPENAI_EMBEDDING_MODEL = 'text-embedding-3-small'
mvn spring-boot:run
```

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
8. `GET /api/v1/sessions/{id}/result`로 사건 재구성 조회

프론트용 `GET /api/v1/sessions/{id}` 응답에는 범인, 진실 요약, 전체 해답,
미발견 단서, NPC의 숨긴 사실이 포함되지 않습니다.

백엔드가 별도 서비스라면 [AI-백엔드 연동 계약](docs/AI_BACKEND_CONTRACT.md)의
`/internal/v1/cases` API를 사용하세요. `AI_INTERNAL_API_KEY`가 설정된 환경에서는
`X-Internal-AI-Key` 헤더가 필수입니다. 운영 환경에서는 이 경로를 사설 네트워크에만
노출해야 합니다.

## 주요 리소스

- `src/main/resources/ai/world/arcadia-world-v1.json`
- `src/main/resources/ai/rules/arcadia-mystery-rules-v1.json`
- `src/main/resources/ai/fallback/sophia-safe-v1.json`
- `src/main/resources/ai/schema/`

## 현재 저장 방식

MVP는 세션과 동결 사건을 인메모리 저장소에 보관합니다. 프로세스 재시작 시 세션이
사라지므로 운영 연결 시 `FrozenCaseBlueprint`와 증거 인벤토리를 DB 저장소로
교체해야 합니다. 도메인 서비스는 저장소 구현과 분리되어 있습니다.
