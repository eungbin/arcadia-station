# Contribution Guide

## Commit messages

이 저장소는 백엔드 저장소와 동일하게 Conventional Commits 형식을 사용합니다.

```text
<type>: <summary>
```

주요 type:

- `feat:` 사용자 또는 서비스 기능 추가
- `fix:` 버그 수정
- `docs:` 문서만 변경
- `test:` 테스트 추가·수정
- `refactor:` 동작 변경 없는 코드 구조 개선
- `chore:` 빌드, 설정, 저장소 관리
- `perf:` 성능 개선

예시:

```text
feat: add persistent AI session storage
fix: enforce internal key on NPC and RAG endpoints
docs: add local backend integration runbook
test: cover fallback RAG response contract
chore: align local service ports
```

모든 커밋에 `feat:`를 붙이지 않습니다. 변경 성격에 맞는 type을 사용해야 히스토리와
릴리스 노트를 정확하게 만들 수 있습니다.

이미 원격에 공유된 과거 커밋은 특별한 합의 없이 rebase/force-push로 다시 쓰지
않습니다. 새 커밋부터 이 규칙을 적용합니다.
