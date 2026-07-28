# Arcadia Station Backend

「아르카디아 스테이션 사건」(NAN 2026) 백엔드 서버 저장소.

작업 시작 전 `docs/arcadia-station-backend-spec.md`를 먼저 읽고 시작할 것.

## 절대 규칙
- Spring Boot 3 / Java 21, 계층형(controller/service/dto) 구조
- 세션·증거 데이터는 반드시 DB(PostgreSQL) 저장 — 인메모리(Map) 금지
- AI 서버는 별도 서비스. 이 저장소는 프록시·게임 세션·판정만 담당, LLM 직접 호출 없음
- AI 서버 NPC/RAG 엔드포인트는 아직 인증 미적용 + 세션이 메모리 전용 저장이라 재시작 시 404 날 수 있음 — 반드시 방어적으로 처리