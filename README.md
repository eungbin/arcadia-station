# Arcadia Station Backend

「아르카디아 스테이션 사건」(NAN 2026) 백엔드 서버.

## 빠른 시작 (Docker Compose)

레포를 로컬로 받은 뒤, 아래 한 줄이면 서버(8080)와 PostgreSQL(5432)이 함께 뜹니다. IDE나 별도 Java/Gradle 설치가 필요 없습니다.

```bash
docker compose up -d --build
```

- 서버: http://localhost:8080
- 헬스체크: http://localhost:8080/actuator/health
- DB: `localhost:5432` (db=`arcadia`, user/password=`arcadia`)

기본 프로필로 뜨기 때문에 AI 서버 없이도 Fake 클라이언트로 전체 플로우(세션 생성 → 탐사/심문/RAG → 추리 제출)가 바로 동작합니다.

로그 확인:

```bash
docker compose logs -f app
```

중지:

```bash
docker compose down
```

DB 데이터까지 초기화하려면:

```bash
docker compose down -v
```

## 로컬 개발(IDE에서 서버만 직접 실행)

DB만 Docker로 띄우고 서버는 IDE/Gradle로 직접 실행하고 싶다면:

```bash
docker compose up -d postgres
./gradlew bootRun
```

## 실제 AI 서버 연동

기본은 로컬 Fake 클라이언트로 동작합니다. 실제 AI 서버에 붙이려면 `real-ai` 프로필과 관련 환경변수가 필요합니다. 자세한 내용은 `docs/arcadia-station-backend-spec.md`와 `docs/ai-server-integration-response.md` 참고.

## 문서

작업 시작 전 `docs/arcadia-station-backend-spec.md`를 먼저 읽을 것. API 명세는 `docs/api-spec.md`, 전체 플레이 흐름은 `docs/gameplay-flow.md` 참고.
