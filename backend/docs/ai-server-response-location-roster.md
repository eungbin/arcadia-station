# AI 서버 탐사 장소 정식 로스터 적용 결과

> 작성일: 2026-07-31
>
> 대상: 게임 백엔드·프론트 개발자
>
> 상태: 구현 및 자동 테스트 완료, GitHub Draft PR 공유
>
> 관련 선례: `docs/BACKEND_NPC_INTERROGATION_COVERAGE_HANDOFF_2026-07-30.md`

## 1. 결론

요청하신 8개 방을 `ARCADIA_WORLD:1.1.0`의 정식 장소 로스터로 확정했습니다.
사건 생성 결과의 모든 장소 참조, 증거 기록 metadata, 프론트 오브젝트 계약과 실제
탐사 API 입력이 같은 8개 ID만 사용하도록 검증을 추가했습니다.

백엔드의 엔드포인트나 DTO 변경은 필요하지 않습니다. 매 세션
`exploreLocationIds`에는 아래 8개 ID를 그대로 전달하면 됩니다. 프론트는 기존
방식대로 자체 좌표·라벨 테이블에 매핑하면 됩니다.

## 2. 백엔드 요청 원문

> # AI 서버팀 전달용 — 탐사 장소(locationId) 정식 로스터 요청
>
> 작성일: 2026-07-31
>
> 관련 선례: `docs/ai-server-integration-response.md` (NPC 심문 대상 커버리지 요청과 같은 종류의 문제)
>
> ## 배경
>
> 프론트에서 아르카디아 스테이션의 고정 맵 UI를 이미 설계해뒀습니다. 구조는 다음과 같습니다.
>
> ```text
> 사령관실(범행 현장·출입기록) ─ 부사령관 집무실(헨드릭스 전용·직통)
>               └────────┬────────┘
>           중앙 허브(복도) — 환경 제어 패널 포함
>    ┌────────┬─────────┬────────┬────────┐
>  의무실   엔지니어링   통신실    화물칸    공용구역
> (소피아)  (백준호)    (카심)  (유나·에어록) (식당·숙소)
> ```
>
> 플레이어는 이 맵 전체를 자유롭게 이동하며 각 방을 탐사
> (`POST /api/v1/sessions/{id}/explore`)할 수 있어야 합니다.
>
> ## 문제
>
> `caseBlueprint.clues[].acquisition.locationId`가 이 8개 방 구조를 따르는 고정
> 로스터가 아니라, 사건마다 즉흥적으로 생성되는 값처럼 보입니다. 저희가 갖고 있는
> 두 샘플을 비교해보니:
>
> | 소스 | locationId 값 | 개수 |
> |---|---|---|
> | 로컬 Fake 픽스처 (`src/main/resources/fixtures/sample-case-blueprint.json`) | `MEDICAL_BAY`, `LIFE_SUPPORT_CORRIDOR`, `PERSONAL_QUARTERS` | 3개 |
> | AI 서버 실제 생성 샘플 (`src/test/resources/ai-server/internal-case-ready.response.json`) | `COMMAND_DECK`, `LIFE_SUPPORT_CONTROL` | 2개 |
>
> 두 샘플 다 위 8개 방 구조와 이름·개수가 맞지 않고, 서로 간에도 겹치는 값이 없습니다.
> `characterId`가 `worldTemplate.characters[].id`라는 정식 로스터를 참조하는 것과
> 달리, 장소는 아직 그런 고정 로스터를 참조하지 않는 것으로 보입니다.
>
> 이건 앞서 확인했던 NPC 심문 커버리지 문제(`alibis`는 5명인데
> `npcKnowledge`는 1명뿐이었던 것)와 같은 계열의 문제입니다 — 프론트가 참조할
> "정식 값 목록"이 AI 서버 쪽에서 아직 고정되지 않은 상태입니다.
>
> ## 요청
>
> 1. 위 8개 방을 `worldTemplate.locations[].id`(또는 동등한 필드)로 정식
>    로스터화해주세요. 프론트 맵 UI가 이미 이 구조로 설계돼 있어서, 가능하면 이 방
>    이름·구성을 그대로 반영해주시면 좋겠습니다.
> 2. 사건 생성 시 `clues[].acquisition.locationId`, `timeline[].locationId`,
>    `method.setupAction/triggerAction.locationId` 등 장소를 참조하는 모든 필드가
>    이 정식 로스터 안의 값만 쓰도록, `characterId` 때 추가하신 것과 같은 종류의
>    교차검증(`InterrogationCoverageCheck`류)을 장소에도 적용해주세요.
> 3. 확정된 로스터 값 목록(ID 문자열 기준)을 공유해주시면, 저희는 이미 구현해둔
>    `exploreLocationIds`(3번 API 응답, 매 세션마다 사건에 실제로 존재하는 탐사 가능
>    장소를 동적으로 내려주는 필드)를 통해 프론트에 그대로 전달하겠습니다. 프론트는
>    이 ID를 받아 자체 맵 좌표/라벨 테이블에 매핑하는 방식으로 렌더링하면 되고,
>    ID 값 자체를 하드코딩하지는 않을 예정입니다.
>
> `characterId` 때와 마찬가지로, 저희 쪽 엔드포인트나 DTO 구조는 변경할 필요가 없고
> 장소 로스터가 확정되는 대로 자동으로 반영됩니다.

## 3. 확정 locationId 로스터

ID는 대소문자를 포함해 아래 문자열이 정식 값입니다.

| 순서 | locationId | 표시 이름 | 맵 역할 |
|---:|---|---|---|
| 1 | `COMMANDER_OFFICE` | 사령관실 | 범행 현장, 출입 기록 |
| 2 | `DEPUTY_COMMANDER_OFFICE` | 부사령관 집무실 | 헨드릭스 전용, 사령관실 직통 |
| 3 | `CENTRAL_HUB` | 중앙 허브 | 연결 복도, 환경 제어 패널 |
| 4 | `MEDICAL_BAY` | 의무실 | 소피아 업무 구역 |
| 5 | `ENGINEERING_BAY` | 엔지니어링 | 백준호 업무 구역 |
| 6 | `COMMUNICATIONS_CENTER` | 통신실 | 카심 업무 구역 |
| 7 | `CARGO_BAY` | 화물칸 | 유나 업무 구역, 에어록 |
| 8 | `COMMON_AREA` | 공용구역 | 식당, 숙소 |

권장 `exploreLocationIds` 값:

```json
[
  "COMMANDER_OFFICE",
  "DEPUTY_COMMANDER_OFFICE",
  "CENTRAL_HUB",
  "MEDICAL_BAY",
  "ENGINEERING_BAY",
  "COMMUNICATIONS_CENTER",
  "CARGO_BAY",
  "COMMON_AREA"
]
```

모든 사건에서 이 8개 방이 월드에 존재하므로 프론트에는 8개를 모두 내려주면 됩니다.
그 시점에 발견할 단서가 없는 방도 탐사 요청 자체는 성공하며
`newlyDiscoveredClues: []`를 반환합니다.

## 4. 맵 연결 관계

- `COMMANDER_OFFICE` ↔ `DEPUTY_COMMANDER_OFFICE`, `CENTRAL_HUB`
- `DEPUTY_COMMANDER_OFFICE` ↔ `COMMANDER_OFFICE`, `CENTRAL_HUB`
- `CENTRAL_HUB` ↔ 나머지 7개 방
- `MEDICAL_BAY`, `ENGINEERING_BAY`, `COMMUNICATIONS_CENTER`, `CARGO_BAY`,
  `COMMON_AREA` ↔ `CENTRAL_HUB`

환경 제어 패널은 `CENTRAL_HUB`, 에어록·도킹 시스템은 `CARGO_BAY`에 포함했습니다.

## 5. 수정한 내용

### 5.1 정식 로스터와 월드 템플릿

- `ArcadiaLocationRoster`에 8개 ID를 단일 코드 상수·순서 목록·집합으로 정의했습니다.
- `arcadia-world-v1.json`의 장소를 요청된 8개 방으로 교체했습니다.
- 인물별 `physicalAccess`, 방 연결 관계, 설치 시스템과 탐사 오브젝트를 새 맵에
  맞췄습니다.
- 기존 장소 구조와 호환되지 않는 변경이므로 월드 템플릿 버전을
  `ARCADIA_WORLD:1.0.0`에서 `ARCADIA_WORLD:1.1.0`으로 올렸습니다.
- `WorldTemplateValidator`는 월드가 정확히 이 8개 ID를 포함하지 않으면
  `LOCATION_ROSTER_MISMATCH`로 시작을 거부합니다.

### 5.2 사건 생성 교차검증

`LocationRosterCheck`를 추가해 다음 모든 필드를 정식 로스터로 검사합니다.

| 검사 경로 | 로스터 밖 ID 처리 |
|---|---|
| `method.setupAction.locationId` | `NON_ROSTER_LOCATION_ID` |
| `method.triggerAction.locationId` | `NON_ROSTER_LOCATION_ID` |
| `timeline[].locationId` | `NON_ROSTER_LOCATION_ID` |
| `clues[].acquisition.locationId` | 값이 있을 때 `NON_ROSTER_LOCATION_ID` |
| `evidenceRecords[].metadata.locationId` | 값이 있을 때 `NON_ROSTER_LOCATION_ID` |

기존 `WorldReferenceCheck`의 `UNKNOWN_LOCATION_ID` 검증도 유지됩니다. 따라서 AI가
새 장소, 오타, 구형 ID 또는 대소문자가 다른 ID를 생성하면 사건은 동결되지 않고
재생성 대상이 됩니다. 재시도 후에도 실패하면 검증된 fallback 사건으로 전환됩니다.

생성 프롬프트에도 장소 ID는 `worldTemplate.locations`의 값을 정확히 사용하고 별칭이나
새 장소를 만들지 말라는 규칙을 추가했습니다.

### 5.3 실제 탐사 API

`POST /api/v1/sessions/{id}/explore`도 `ArcadiaLocationRoster`를 검사합니다.

- 정식 8개 ID: `200 OK`
- 정식 방이지만 새 단서 없음: `200 OK`, `newlyDiscoveredClues: []`
- 구형·임의·오타 ID: `400 INVALID_REQUEST`

즉 생성 데이터만 맞고 런타임에서 임의 장소를 허용하는 틈도 막았습니다.

### 5.4 폴백·샘플·프론트 계약

- 검증 완료 fallback 사건의 수법·타임라인·단서·증거 metadata 장소를 새 로스터로
  변경했습니다.
- 사령관실을 범행 현장, 중앙 허브를 환경 제어 패널 위치로 반영했습니다.
- 내부 READY 응답 샘플과 SHA-256을 새 사건 내용에 맞게 갱신했습니다.
- 프론트 오브젝트 16개의 위치 매핑을 새 로스터로 옮기고 프론트 통합 계약 버전을
  `1.1.0`으로 올렸습니다.

구형 ID는 alias로 허용하지 않습니다. 데이터 이전 시 의미에 따라 다음처럼 바꿔야
합니다.

| 구형 ID | 새 위치 |
|---|---|
| `COMMAND_DECK` | 일반적으로 `COMMANDER_OFFICE`; 부사령관 전용 데이터는 `DEPUTY_COMMANDER_OFFICE` |
| `SECURITY_HUB` | 출입 기록 대상 방인 `COMMANDER_OFFICE` 또는 실제 대상 방 |
| `LIFE_SUPPORT_CONTROL` | `CENTRAL_HUB` |
| `COMMAND_CORRIDOR` | `CENTRAL_HUB`; 식당·숙소 데이터는 `COMMON_AREA` |
| `MAINTENANCE_CORRIDOR` | `CENTRAL_HUB` |
| `DOCKING_CONTROL` | `CARGO_BAY` |

## 6. 자동 테스트 결과

테스트 환경:

- Microsoft OpenJDK `21.0.12`
- Apache Maven `3.9.16`
- 명령: `mvn test`

결과:

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

추가·강화한 핵심 검증:

- 월드 템플릿 버전과 8개 로스터의 정확한 순서·구성
- method, timeline, clue acquisition, evidence metadata의 비정식 장소 거부
- 8개 장소 전부에 대한 실제 탐사 API `200 OK`
- 구형 `LIFE_SUPPORT_CONTROL` 탐사 요청의 `400 INVALID_REQUEST`
- 프론트 오브젝트 16개가 모두 새 정식 로스터 안의 ID만 사용하는지 확인
- fallback, 사건 스키마, NPC 커버리지, RAG, 심문, 추리 판정 등 기존 전체 회귀 테스트

JDK와 Maven은 이 컴퓨터의 임시 테스트 도구일 뿐 Git에 포함하지 않았습니다. 다른
컴퓨터에서는 JDK 21과 Maven으로 같은 커밋에서 `mvn test`를 실행하면 동일한 소스와
테스트를 검증할 수 있습니다.

## 7. Git 공유 정보

- 저장소: `tyoonkk/GAME_AI`
- 브랜치: `agent/npc-interrogation-coverage`
- 대상 브랜치: `main`
- Draft PR: <https://github.com/tyoonkk/GAME_AI/pull/3>

이번 장소 수정은 앞선 NPC 심문 커버리지 수정과 같은 통합 이슈이므로 동일한 Draft
PR에 별도 후속 커밋으로 올립니다. 연구실 컴퓨터에서 원격 브랜치를 받아 비교하고
문제가 없으면 PR을 Ready 상태로 바꾼 뒤 `main`에 병합하면 됩니다.

연구실 컴퓨터 확인 명령:

```powershell
git fetch origin
git switch agent/npc-interrogation-coverage
git pull --ff-only
mvn test
git diff origin/main...HEAD
```

## 8. 백엔드 개발자에게 보낼 답변

아래 내용을 그대로 전달하셔도 됩니다.

> 요청하신 탐사 장소 정식 로스터 적용을 완료했습니다.
>
> `ARCADIA_WORLD`를 `1.1.0`으로 올리고 다음 8개 `locationId`를 정식 값으로
> 확정했습니다:
>
> `COMMANDER_OFFICE`, `DEPUTY_COMMANDER_OFFICE`, `CENTRAL_HUB`, `MEDICAL_BAY`,
> `ENGINEERING_BAY`, `COMMUNICATIONS_CENTER`, `CARGO_BAY`, `COMMON_AREA`
>
> `method.setupAction/triggerAction.locationId`, `timeline[].locationId`,
> `clues[].acquisition.locationId`뿐 아니라 기존 검사에서 빠질 수 있던
> `evidenceRecords[].metadata.locationId`까지 동일한 로스터로 교차검증합니다.
> 로스터 밖 값은 `NON_ROSTER_LOCATION_ID`로 사건 동결 전에 거부되며, 월드 템플릿
> 자체가 정확한 8개 구성이 아니면 `LOCATION_ROSTER_MISMATCH`로 거부됩니다.
>
> 실제 `POST /api/v1/sessions/{id}/explore`도 같은 로스터를 검사하도록 수정했습니다.
> 8개 방은 모두 자유롭게 탐사할 수 있고, 발견 단서가 없는 경우에도 200과 빈 단서
> 배열을 반환합니다. 구형 또는 임의 ID는 400 `INVALID_REQUEST`입니다.
>
> fallback 사건, READY 응답 샘플, 프론트 오브젝트 위치 매핑도 새 ID로 갱신했습니다.
> 백엔드 엔드포인트나 DTO 변경은 필요 없습니다. `exploreLocationIds`에는 위 8개 ID를
> 그대로 내려주시면 됩니다.
>
> Microsoft OpenJDK 21.0.12 / Maven 3.9.16 환경에서 전체 `mvn test`를 실행했고
> 34개 테스트가 모두 통과했습니다(실패 0, 오류 0, 스킵 0).
>
> 변경은 `agent/npc-interrogation-coverage` 브랜치와 Draft PR #3에 공유했습니다:
> <https://github.com/tyoonkk/GAME_AI/pull/3>

## 9. 관련 파일

- `src/main/java/com/arcadia/station/ai/template/ArcadiaLocationRoster.java`
- `src/main/java/com/arcadia/station/ai/validation/checks/LocationRosterCheck.java`
- `src/main/java/com/arcadia/station/ai/validation/WorldTemplateValidator.java`
- `src/main/java/com/arcadia/station/game/application/ExplorationService.java`
- `src/main/java/com/arcadia/station/ai/casegen/CasePromptAssembler.java`
- `src/main/resources/ai/world/arcadia-world-v1.json`
- `src/main/resources/ai/fallback/sophia-safe-v1.json`
- `src/main/resources/integration/frontend-contract-v1.json`
- `docs/examples/internal-case-ready.response.json`
- `docs/AI_BACKEND_CONTRACT.md`
- `docs/FRONTEND_BACKEND_AI_BRIDGE.md`
