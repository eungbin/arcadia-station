# AI 서버 NPC 심문 대상 커버리지 수정 및 백엔드 전달서

> 작성일: 2026-07-30
>
> AI 서버 저장소: `tyoonkk/GAME_AI`
>
> 전달 브랜치: `agent/npc-interrogation-coverage`
>
> 대상 계약: `caseBlueprint.alibis[]`, `caseBlueprint.npcKnowledge[]` 및 사건 내 `characterId` 참조

## 1. 백엔드 개발자의 최초 요청

아래는 백엔드 개발자가 전달한 요청 전문입니다.

> **AI 서버팀 전달용 — NPC 심문 대상 커버리지 요청**
>
> 안녕하세요, 최근 사건 생성 응답 검토하면서 확인된 내용 공유드립니다.
>
> **배경**
>
> 저희 게임 설계상 플레이어가 사건에 등장하는 용의자 전원을 심문할 수 있어야 합니다.
> 백엔드는 `caseBlueprint.alibis[]`에 있는 `characterId` 전체를 프론트에
> "심문 가능한 인물 목록"으로 그대로 노출할 예정입니다.
>
> **문제**
>
> 실제 연동 검증용으로 주신 샘플(`internal-case-ready.response.json`)을 확인해보니:
>
> - `alibis[]`엔 용의자 5명이 있습니다 (`SOPHIA`, `MAYA`, `JUNHO`, `KASIM`, `YUNA`)
> - `npcKnowledge[]`엔 범인(`SOPHIA`) 1명만 있습니다
>
> 저희 백엔드는 스펙 5.3절 규칙("AI 응답의 `revealedFactIds`는 반드시
> `npcKnowledge` 기준으로 화이트리스트 재검증한다" — AI가 잘못된 사실을 흘려도
> 걸러내기 위한 보안 장치)에 따라, 심문 요청이 들어오면 해당 `characterId`의
> `npcKnowledge`가 있는지부터 확인합니다. 없으면 안전하게 검증할 기준이 없다고
> 판단해 요청 자체를 막습니다(400).
>
> 그 결과 지금 상태로는 프론트에 5명 버튼이 다 뜨지만, 범인이 아닌 4명은 눌러도
> 전부 400이 나서 사실상 심문이 안 됩니다.
>
> **요청 1 — npcKnowledge 커버리지**
>
> `npcKnowledge`가 `alibis`에 등장하는 용의자 전원(범인 포함)에 대해 생성되도록
> 해주세요. 범인이 아니라서 숨길 결정적 사실이 없는 인물이라도, 최소한 아래는
> 채워져야 심문 자체가 가능합니다.
>
> - `initialClaimFactIds` (알리바이 진술과 연결된 사실)
> - `recommendedQuestionTopics` (추천 질문, AI 응답 실패 시 폴백에도 쓰임)
> - `revealPolicies`는 비어 있어도 무방(공개할 결정적 사실이 없는 경우)
>
> **요청 2 — characterId 참조 일관성**
>
> 한 사건 안에서 같은 인물을 가리키는 `characterId`는 `culpritId`, `alibis[]`,
> `npcKnowledge[]`, `solution.nonCulpritExclusions[]`, `clues[].suspectEffects[]`
> 전부에서 정확히 동일한 문자열이어야 합니다. `case-blueprint.schema.json`엔 이
> 필드 간 일치 여부를 검사하는 제약이 없어서(타입만 string), 생성 로직/검증기
> 쪽에서 별도로 보장해주셔야 합니다. 오타나 표기 차이(예: `alibis`엔 `"JUNHO"`,
> `npcKnowledge`엔 `"Junho"`)가 있으면 저희 화이트리스트 매칭이 깨집니다.
>
> **참고**
>
> `characterId` 값 자체(예: `"SOPHIA"`라는 이름)가 매 사건마다 달라지는 건 전혀
> 문제 없습니다. 저희는 매 세션마다 `alibis` 기준으로 동적으로 목록을 받아 쓰도록
> 이미 구현했습니다. 다만 한 사건 내에서는 `npcKnowledge`가 전 용의자를 빠짐없이,
> 그리고 일관된 ID로 커버해야 합니다.
>
> 확인 부탁드립니다.

## 2. 확인 결과와 원인

백엔드의 재현과 원인 분석이 정확했습니다.

수정 전 내장 fallback 사건과 전달 샘플은 다음 상태였습니다.

| 필드 | 수정 전 값 |
|---|---|
| `alibis[].characterId` | `MAYA`, `JUNHO`, `SOPHIA`, `KASIM`, `YUNA` |
| `npcKnowledge[].characterId` | `SOPHIA` |

AI 서버의 `NpcContextFactory`도 요청의 `characterId`와 정확히 일치하는
`npcKnowledge`를 먼저 찾습니다. 따라서 나머지 4명은 심문 컨텍스트가 없어
`400 INVALID_REQUEST`가 발생하는 것이 기존 코드상 정상 동작이었습니다.

백엔드의 `revealedFactIds` 화이트리스트 재검증과 `npcKnowledge`가 없는 인물에 대한
요청 차단은 보안상 타당합니다. 이 검증을 완화하지 않고 AI 서버의 생성·검증 계약을
강화하는 방향으로 수정했습니다.

## 3. 반영한 수정

### 3.1 사건 생성 프롬프트

`CasePromptAssembler`에 다음 생성 규칙을 추가했습니다.

- `alibis`에 모든 용의자를 정확히 한 번씩 포함
- `alibis`의 모든 `characterId`에 대해 `npcKnowledge` 생성
- `initialClaimFactIds`에 해당 알리바이와 연결된 사실을 최소 1개 포함
- `recommendedQuestionTopics`를 최소 1개 포함
- 공개할 결정적 사실이 없는 인물은 `revealPolicies: []` 허용
- `worldTemplate.characters[].id`를 대소문자까지 그대로 사용
- 사건 내 모든 인물 참조에서 같은 인물을 다른 문자열로 표기하지 않음

### 3.2 서버 측 의미 검증기

JSON Schema만으로 검사할 수 없는 교차 필드 규칙을 담당하도록
`InterrogationCoverageCheck`를 추가했습니다.

검증하는 조건은 다음과 같습니다.

1. `culpritId`가 세계관의 유효한 용의자인지 확인
2. `alibis[].characterId`의 중복과 비(非)용의자 포함 여부 확인
3. `alibis`의 모든 인물에 `npcKnowledge`가 존재하는지 확인
4. 같은 `characterId`의 `npcKnowledge` 중복 여부 확인
5. `initialClaimFactIds`가 비어 있지 않은지 확인
6. `initialClaimFactIds`가 해당 인물의 알리바이 사실에 연결되는지 확인
7. `recommendedQuestionTopics`가 비어 있지 않은지 확인
8. `solution.nonCulpritExclusions`가 범인을 제외한 용의자 전원을 정확히 한 번씩
   포함하는지 확인
9. `clues[].suspectEffects[].characterId`가 실제 알리바이 용의자를 참조하는지 확인
10. 기존 `WorldReferenceCheck`와 함께 모든 인물 ID를
    `worldTemplate.characters[].id`에 대해 대소문자 구분으로 확인
11. 기존 검증으로 `culpritId == solution.culpritId`를 확인

주요 신규 검증 오류 코드는 다음과 같습니다.

| 오류 코드 | 의미 |
|---|---|
| `DUPLICATE_ALIBI_CHARACTER_ID` | 동일 인물의 알리바이가 중복됨 |
| `CULPRIT_NOT_SUSPECT` | 범인 ID가 정식 용의자 ID가 아님 |
| `NON_SUSPECT_ALIBI` | 알리바이에 비(非)용의자가 포함됨 |
| `DUPLICATE_NPC_KNOWLEDGE_CHARACTER_ID` | 동일 인물의 NPC 지식이 중복됨 |
| `MISSING_NPC_KNOWLEDGE` | 알리바이 인물의 NPC 지식이 누락됨 |
| `MISSING_INITIAL_CLAIM_FACT` | 최초 진술 사실이 비어 있음 |
| `INITIAL_CLAIM_FACT_NOT_LINKED_TO_ALIBI` | 최초 진술 사실이 알리바이와 연결되지 않음 |
| `MISSING_RECOMMENDED_QUESTION_TOPIC` | 추천 질문 주제가 비어 있음 |
| `DUPLICATE_NON_CULPRIT_EXCLUSION` | 비범인 배제 항목이 중복됨 |
| `MISSING_NON_CULPRIT_EXCLUSION` | 비범인 배제 항목이 누락됨 |
| `INVALID_NON_CULPRIT_EXCLUSION` | 범인 또는 알리바이 외 인물이 비범인 배제에 포함됨 |
| `NON_ALIBI_SUSPECT_EFFECT` | 단서 효과가 알리바이 외 인물을 참조함 |

위 검증에 실패한 AI 생성 결과는 동결하지 않습니다. 기존 사건 생성 흐름에 따라
재생성하고, 최대 재시도 후에도 실패하면 검증 완료 fallback 사건으로 전환합니다.

### 3.3 내장 fallback 사건

`sophia-safe-v1.json`을 다음과 같이 보완했습니다.

- `SOPHIA`, `MAYA`, `JUNHO`, `KASIM`, `YUNA` 전원의 `npcKnowledge` 제공
- 각 인물에 알리바이 최초 진술 전용 `CLAIM` 사실 추가
- 각 `initialClaimFactIds`를 해당 인물의 `CLAIM` 사실에 연결
- 각 인물에 2개 이상의 `recommendedQuestionTopics` 제공
- 공개할 결정적 사실이 없는 비범인은 `revealPolicies: []`
- 비범인 최초 진술에서 `EXCLUSION` 사실을 직접 노출하지 않도록 분리

현재 fallback의 심문 대상 커버리지는 다음과 같습니다.

| characterId | initialClaimFactIds | revealPolicies | 추천 질문 |
|---|---|---:|---:|
| `SOPHIA` | `FACT-SOPHIA-CLAIM` | 3개 | 3개 |
| `MAYA` | `FACT-MAYA-CLAIM` | 0개 | 2개 |
| `JUNHO` | `FACT-JUNHO-CLAIM` | 0개 | 2개 |
| `KASIM` | `FACT-KASIM-CLAIM` | 0개 | 2개 |
| `YUNA` | `FACT-YUNA-CLAIM` | 0개 | 2개 |

### 3.4 전달 샘플과 계약 문서

다음 전달 자료를 갱신했습니다.

- `docs/AI_BACKEND_CONTRACT.md`
- `docs/examples/internal-case-ready.response.json`
- `output/pdf/internal-case-ready.response.json`

갱신된 샘플의 `blueprintSha256`은 다음과 같습니다.

```text
0af49526c2f410a5ac52b739826849fe9017f20e4418b84338666450389b5f38
```

`case-blueprint.schema.json`의 구조는 변경하지 않았습니다. 이번 요청은 배열 간 참조와
집합 관계를 검사해야 하므로 JSON Schema가 아닌 서버 의미 검증기에서 보장합니다.

## 4. 요청별 충족 결과

| 백엔드 요청 | 반영 결과 | 보장 방식 |
|---|---|---|
| `alibis` 전원의 `npcKnowledge` | 충족 | `MISSING_NPC_KNOWLEDGE` 검증 |
| 범인 포함 전 용의자 심문 가능 | 충족 | fallback 데이터 및 HTTP 통합 테스트 |
| `initialClaimFactIds` 제공 | 충족 | 비어 있음 및 알리바이 연결 검증 |
| `recommendedQuestionTopics` 제공 | 충족 | 비어 있음 검증 |
| 비범인 `revealPolicies: []` 허용 | 충족 | 빈 배열 fallback 및 심문 테스트 |
| 대소문자를 포함한 정확한 `characterId` | 충족 | 정식 세계관 ID에 대한 case-sensitive 검증 |
| `culpritId`와 `solution.culpritId` 일치 | 충족 | 기존 `SOLUTION_CULPRIT_MISMATCH` 검증 |
| 비범인 배제 목록 일관성 | 충족 | 기대 집합과 선언 집합의 정확한 비교 |
| `suspectEffects` 인물 참조 일관성 | 충족 | 알리바이 용의자 집합에 대한 검증 |
| 잘못 생성된 AI 응답 차단 | 충족 | 의미 검증 실패 후 재시도/fallback |

## 5. 추가한 회귀 테스트

`CaseBlueprintValidatorTest`에 다음 사례를 추가했습니다.

- 알리바이 인물의 `npcKnowledge` 누락 거부
- 동일 인물의 `npcKnowledge` 중복 거부
- 빈 `initialClaimFactIds` 거부
- 빈 `recommendedQuestionTopics` 거부
- 알리바이에 연결되지 않은 최초 진술 사실 거부
- `"MAYA"`와 `"Maya"` 같은 대소문자 불일치 거부
- 누락된 비범인 배제 항목 거부
- 알리바이 외 인물에 대한 `suspectEffects` 거부

`GameFlowIntegrationTest`에는 READY 사건의 `alibis`에서 심문 대상 목록을 읽어
5명 전원에게 실제 HTTP 심문 요청을 보내는 테스트를 추가했습니다.

검증된 인물:

```text
MAYA
JUNHO
SOPHIA
KASIM
YUNA
```

다섯 요청 모두 `200 OK`와 추천 질문 2개를 반환하는 것을 확인했습니다.

## 6. 테스트 결과

### 6.1 집중 회귀 테스트

실행 명령:

```powershell
mvn "-Dtest=CaseBlueprintValidatorTest,GameFlowIntegrationTest" test
```

결과:

```text
Tests run: 17
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

### 6.2 프로젝트 전체 테스트

실행 명령:

```powershell
mvn test
```

Surefire 전체 결과:

```text
Test suites: 10
Tests run: 32
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

### 6.3 추가 정합성 검증

- fallback JSON 파싱 성공
- 문서용 READY 응답 JSON 파싱 성공
- PDF 전달 디렉터리의 READY 응답 JSON 파싱 성공
- 세계관 용의자 집합과 `alibis` 집합 일치
- `alibis ⊆ npcKnowledge` 확인
- 비범인 배제 집합이 `용의자 - 범인`과 일치
- 모든 `suspectEffects.characterId`가 알리바이 용의자 집합에 포함
- 모든 `initialClaimFactIds`가 해당 인물의 `CLAIM`이며 알리바이에 연결
- fallback과 두 READY 샘플의 `caseBlueprint` 완전 일치
- 샘플의 `blueprintSha256` 재계산 결과 일치
- `git diff --check` 통과

### 6.4 테스트 환경

```text
Windows x64
Microsoft OpenJDK 21.0.12
Apache Maven 3.9.16
Spring Boot 3.3.3
```

외부 Gemini/OpenAI를 직접 호출하는 라이브 테스트는 실행하지 않았습니다. 그러나
실제 모델이 이 계약을 위반한 사건을 반환하면 서버 의미 검증기가 READY 동결을
차단하고 재시도 또는 검증 완료 fallback으로 전환하므로 백엔드가 받는 READY 사건의
계약은 유지됩니다.

## 7. 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `src/main/java/com/arcadia/station/ai/casegen/CasePromptAssembler.java` | 전 용의자 NPC 지식 및 ID 일관성 생성 지시 |
| `src/main/java/com/arcadia/station/ai/validation/checks/InterrogationCoverageCheck.java` | 신규 교차 필드 의미 검증기 |
| `src/main/resources/ai/fallback/sophia-safe-v1.json` | 5명 전원의 심문 컨텍스트 |
| `src/test/java/com/arcadia/station/ai/validation/CaseBlueprintValidatorTest.java` | 검증기 회귀 테스트 |
| `src/test/java/com/arcadia/station/game/api/GameFlowIntegrationTest.java` | 5명 전원 심문 HTTP 테스트 |
| `docs/AI_BACKEND_CONTRACT.md` | 심문 대상 및 ID 계약 명시 |
| `docs/examples/internal-case-ready.response.json` | 갱신된 READY 전체 응답 |
| `output/pdf/internal-case-ready.response.json` | 전달용 READY 응답 복사본 |

## 8. 백엔드 적용 안내

백엔드는 기존 구현 방식을 유지하면 됩니다.

1. `caseBlueprint.alibis[].characterId` 전체를 심문 가능한 인물로 노출
2. 심문 요청 시 동일한 문자열을 URL의 `{characterId}`로 사용
3. AI 응답의 `revealedFactIds`를 해당 인물의 `npcKnowledge` 기준으로 재검증
4. 인물 ID를 임의로 소문자화하거나 표시명으로 변환하지 않음
5. 새 READY fixture를 사용하는 테스트에서는 갱신된 JSON과 SHA-256을 반영

NPC 심문 요청·응답 필드와 엔드포인트는 변경되지 않았습니다.

```http
POST /api/v1/sessions/{sessionId}/interrogations/{characterId}/turns
```

백엔드의 화이트리스트 보안 장치를 완화하거나 제거할 필요가 없습니다.

## 9. 백엔드 개발자에게 바로 전달할 메시지

```text
안녕하세요. 요청해주신 NPC 심문 대상 커버리지와 characterId 참조 일관성 문제를
AI 서버에 반영했습니다.

기존 샘플과 fallback에서 alibis는 5명이지만 npcKnowledge는 SOPHIA 1명뿐이었던
문제를 확인했고, 백엔드의 화이트리스트 검증을 완화하지 않고 AI 서버의
생성 프롬프트·의미 검증기·fallback 데이터를 수정했습니다.

이제 alibis의 모든 인물에 npcKnowledge가 존재하며 각 인물은
initialClaimFactIds와 recommendedQuestionTopics를 가집니다. 공개할 결정적 사실이
없는 비범인은 revealPolicies가 빈 배열입니다.

또한 culpritId, alibis, npcKnowledge, solution.nonCulpritExclusions,
clues[].suspectEffects[]의 characterId를 worldTemplate의 정식 ID에 대해
대소문자까지 검증합니다. 누락·중복·불일치가 있는 사건은 READY로 동결되지 않고
재생성 또는 검증된 fallback으로 전환됩니다.

검증 결과:
- 집중 회귀 테스트 17개 통과
- 프로젝트 전체 테스트 32개 통과
- 실패 0, 오류 0, 건너뜀 0
- alibis에 노출되는 MAYA, JUNHO, SOPHIA, KASIM, YUNA 전원 심문 API 200 확인
- READY 샘플과 blueprintSha256 정합성 확인

요청·응답 엔드포인트나 DTO 필드는 변경되지 않았으며, 백엔드는 기존
revealedFactIds 화이트리스트 검증을 그대로 유지하면 됩니다.

상세 수정 및 테스트 결과:
docs/BACKEND_NPC_INTERROGATION_COVERAGE_HANDOFF_2026-07-30.md

갱신된 계약:
docs/AI_BACKEND_CONTRACT.md

갱신된 전체 READY 샘플:
docs/examples/internal-case-ready.response.json
```

## 10. 연구실 컴퓨터에서 비교 후 main 반영

현재 브랜치를 가져온 뒤 `main`과 비교합니다.

```powershell
git status
git fetch origin
git switch --track origin/agent/npc-interrogation-coverage
git diff origin/main...HEAD
git log --oneline --decorate origin/main..HEAD
```

연구실 컴퓨터에 별도의 미커밋 작업이 있다면 먼저 별도 브랜치에 커밋하거나 stash한
후 비교해야 합니다.

차이가 의도한 수정뿐이고 테스트도 통과하면 `main`을 최신화한 다음 fast-forward로
반영할 수 있습니다.

```powershell
git switch main
git pull --ff-only origin main
git merge --ff-only origin/agent/npc-interrogation-coverage
git push origin main
```

원격 `main`에 다른 커밋이 추가되어 fast-forward가 불가능하면 강제로 푸시하지 말고
GitHub Pull Request로 검토·병합합니다.
