# 단서 문맥·추리 판정 확장 요청 문서

> 작성일: 2026-08-06
>
> 대상: 게임 백엔드 개발자 (필요 시 AI 서버 개발자)
>
> 기준 커밋: `84837b6d90ed44db57bb9ca13daad98d5dff3f0b`
>
> 요청 범위: 요청 A(단서 문맥), 요청 B(배제 판정), 요청 C(결과 공개 조건). 세 건 모두 독립적으로 적용 가능합니다.

## 1. 요청 요약

플레이 피드백에서 다음 세 가지가 나왔습니다.

1. 오브젝트를 조사한 뒤 **이 단서를 무엇과 비교해야 하는지** 알 수 없다.
2. 틀린 추리에 대해 **정답 대신 부족한 논리**를 알려 달라. (예: "마야의 알리바이를 배제할 근거가 없습니다")
3. 엔딩 뒤에 **정답 해설 타임라인**을 보여 달라.

세 가지 모두 **필요한 데이터는 이미 `CaseBlueprint`에 있고 AI 스키마가 필수로 강제**하고 있습니다. 막혀 있는 것은 플레이어에게 나가는 DTO의 폭과 API 공개 조건뿐입니다.

## 2. 지금 막혀 있는 지점

### 2.1 단서 DTO가 4필드로 잘려 있음

`CaseBlueprint.clues[]`(도메인 [`Clue.java`](../backend/src/main/java/com/arcadia/station/domain/caseblueprint/Clue.java))는 다음을 이미 갖고 있습니다.

| 필드 | 내용 | 스키마 필수 여부 |
| --- | --- | --- |
| `solutionRoles` | 이 단서가 어느 역할(SETUP/TRIGGER/OPPORTUNITY/MOTIVE)의 증거인지 | 필수 |
| `revealsFactIds` | 이 단서가 드러내는 사실 ID | 필수 |
| `suspectEffects` | 인물별 SUPPORTS / EXCLUDES / NEUTRAL | 필수 |
| `acquisition.requiredClueIds` | 이 단서를 여는 데 필요한 선행 단서 | 필수 |
| `isCore` | 핵심 단서 여부 | 필수 |

필수 여부는 `ai-server/src/main/resources/ai/schema/case-blueprint.schema.json`의 `$defs.clue.required`로 확인했습니다. 즉 **모든 생성 사건에 항상 존재**합니다.

그런데 플레이어에게 나가는 [`PlayerClueView.java`](../backend/src/main/java/com/arcadia/station/dto/response/PlayerClueView.java)는 `clueId / title / clueType / playerText` 4개뿐이라, 프론트가 단서 사이의 관계를 표시할 근거가 전혀 없습니다.

### 2.2 배제 근거가 서버로 전송되지 않음

프론트 사건 재구성 화면은 범인 외 용의자마다 배제 근거를 고르게 합니다. 그런데 [`DeductionRequest.java`](../backend/src/main/java/com/arcadia/station/dto/request/DeductionRequest.java)는 `culpritId`, `evidenceByRole` 두 필드뿐이라 **배제 근거가 서버에 도달하지 않습니다.** 백엔드는 `solution.nonCulpritExclusions`를 갖고 있으면서 판정에 쓰지 않습니다.

### 2.3 결과 재구성이 정답일 때만 열림

`GET /sessions/{id}/result`는 `truthSummary`, `method`, `timeline`, `facts`, `alibis`, `solution`을 모두 반환합니다. 하지만 `SessionState.COMPLETED`에서만 200이고, `COMPLETED`는 판정이 `CORRECT`일 때만 설정됩니다([`DeductionService.submit`](../backend/src/main/java/com/arcadia/station/service/DeductionService.java)). **틀린 채로 게임이 끝난 플레이어는 해설을 영영 볼 수 없습니다.**

## 3. 요청 A — 단서 문맥 (`PlayerClueView` 확장)

### 3.1 응답 형태

`GET /sessions/{id}`의 `discoveredClues[]`와 탐사·검색 응답의 단서 항목에 아래 필드를 추가해 주십시오. 기존 4필드는 그대로 둡니다(프론트 하위 호환).

```jsonc
{
  "clueId": "CLUE-SETUP-LOG",
  "title": "의료 물자 반출 승인 기록",
  "clueType": "DIGITAL",
  "playerText": "…",

  // --- 추가 요청 ---
  "isCore": true,
  "revealedFacts": [
    { "factId": "FACT-SETUP", "statement": "소피아가 의료 물자 반출을 승인했다." }
  ],
  "linkedClueIds": ["CLUE-ACCESS-HISTORY"],
  "suspectEffects": [
    { "characterId": "SOPHIA", "effect": "SUPPORTS" }
  ],
  "hasPendingConnection": true
}
```

### 3.2 필드별 계산 방법과 스포일러 경계

| 필드 | 계산 | 스포일러 경계 |
| --- | --- | --- |
| `isCore` | `clue.isCore()` 그대로 | 없음. "이건 곁가지가 아니다" 수준 |
| `revealedFacts` | `clue.revealsFactIds()` → `blueprint.facts()` 조회 → `{factId, statement}` | **`truthValue`는 절대 내리지 마십시오.** 그 값은 "이 진술이 거짓인지"라서 그대로 정답입니다. `statement`만 필요합니다 |
| `linkedClueIds` | 같은 `factId`를 공유하는 다른 단서 중 **플레이어가 이미 발견한 것만** | 미발견 단서 ID가 섞이면 존재를 유출합니다. `EvidenceInventory.discoveredClueIds`로 반드시 걸러 주십시오 |
| `suspectEffects` | `clue.suspectEffects()` 그대로 | 없음. 어차피 `playerText`를 읽으면 누구에게 불리한지 알 수 있는 수준입니다 |
| `hasPendingConnection` | 아직 발견하지 않은 `CONNECT` 단서 중 `acquisition.requiredClueIds`에 이 단서를 포함한 것이 하나라도 있으면 `true` | **boolean만 내려 주십시오.** 대상 `clueId`를 내리면 아직 못 찾은 단서를 지목하게 됩니다 |

`hasPendingConnection`이 요청 A의 핵심입니다. 프론트는 이걸로 "이 기록은 아직 다른 기록과 맞물리지 않았습니다"만 표시하고, 무엇과 맞물리는지는 플레이어가 찾게 둡니다. 피드백의 "무엇과 비교해야 하는지"를 정답 공개 없이 만족시키는 선입니다.

### 3.3 `solutionRoles`는 요청하지 않습니다

증거 카드에 `준비 / 실행 / 기회 / 동기` 태그를 붙이자는 피드백이 있었지만, **`solutionRoles`를 그대로 내리면 최종 추리의 정답이 그대로 노출됩니다.** `DeductionService.isCorrectForRole()`이 `clue.solutionRoles()`에 해당 역할이 있는지를 판정 기준의 절반으로 쓰기 때문에, SETUP 태그가 붙은 카드를 고르면 그 역할은 무조건 맞습니다.

프론트는 이 태그를 **플레이어가 직접 붙이는 방식**으로 구현할 예정입니다. 백엔드 변경이 필요 없고, "이 증거를 어디에 쓸 것인가"를 플레이어가 판단하게 되어 추리 게임으로서도 더 맞습니다. `solutionRoles` 노출 요청은 앞으로도 하지 않겠습니다.

## 4. 요청 B — 배제 근거 제출과 판정

### 4.1 요청 확장

```jsonc
// POST /api/v1/sessions/{id}/deductions
{
  "culpritId": "SOPHIA",
  "evidenceByRole": {
    "SETUP": "CLUE-SETUP-LOG",
    "TRIGGER": "CLUE-TRIGGER-LOG",
    "OPPORTUNITY": "CLUE-ACCESS-HISTORY",
    "MOTIVE": "CLUE-MOTIVE-MESSAGE"
  },

  // --- 추가 요청. 범인으로 지목하지 않은 용의자 전원에 대해 보냅니다 ---
  "exclusionsByCharacter": {
    "MAYA": "CLUE-ACCESS-HISTORY",
    "MARCUS": "CLUE-ACCESS-HISTORY"
  }
}
```

**하위 호환 요청:** `exclusionsByCharacter`가 없거나 비어 있으면 지금과 똑같이 동작하게 해 주십시오. 프론트 배포와 백엔드 배포 순서가 어긋나도 게임이 멈추지 않아야 합니다.

같은 단서를 여러 명의 배제에 재사용하는 것은 허용해 주십시오. 사건당 단서가 5개 안팎이라 배제마다 고유 단서를 요구하면 재판을 열 수 없고, 출입 기록 하나로 여러 명을 동시에 배제하는 것은 추리로서도 자연스럽습니다.

### 4.2 응답 확장

```jsonc
{
  "verdict": "PARTIAL",
  "culpritCorrect": true,
  "roleResults": { "SETUP": "CORRECT", "TRIGGER": "INCORRECT", "OPPORTUNITY": "CORRECT", "MOTIVE": "CORRECT" },
  "remainingAttempts": 2,
  "feedback": "범인은 맞지만 실행 트리거 증거를 다시 확인해야 합니다.",

  // --- 추가 요청 ---
  "exclusionResults": { "MAYA": "CORRECT", "MARCUS": "INSUFFICIENT" },
  "missingLogic": [
    { "code": "WEAK_ROLE_EVIDENCE", "role": "TRIGGER", "message": "실행 시점을 입증할 증거가 부족합니다." },
    { "code": "WEAK_EXCLUSION", "characterId": "MARCUS", "message": "마커스를 배제할 근거가 부족합니다." }
  ]
}
```

- `exclusionResults[characterId]`: 제출한 단서가 `solution.nonCulpritExclusions`의 해당 인물 `excludedByClueIds`에 포함되면 `CORRECT`, 아니면 `INSUFFICIENT`.
- `missingLogic`: 이미 `buildFeedback()`이 만들고 있는 문장을 **구조화된 배열로 한 번 더** 내려 달라는 요청입니다. 프론트가 항목별로 짚어서 보여주려면 문자열 한 덩어리로는 부족합니다.
- `code`는 프론트 분기용입니다. 최소 `WEAK_ROLE_EVIDENCE`, `WEAK_EXCLUSION`, `WRONG_CULPRIT` 세 가지면 충분합니다.
- **정답 단서 ID나 정답 인물은 여기에 담지 마십시오.** 지금 `buildFeedback()`이 지키고 있는 9.3절 경계를 그대로 유지해 주십시오. "무엇이 부족한지"까지만 말하고 "무엇이 정답인지"는 말하지 않습니다.

### 4.3 프론트가 함께 고칠 부분

현재 프론트는 이 응답에서 `verdict`만 쓰고 `roleResults`·`remainingAttempts`·`feedback`을 버린 뒤 곧바로 엔딩으로 갑니다. 백엔드는 이미 오답 3회를 허용하는데 프론트가 1회로 끝내고 있었습니다. 이건 프론트 쪽 문제이므로 저희가 고칩니다. 백엔드는 위 두 필드만 추가해 주시면 됩니다.

## 5. 요청 C — 결과 재구성 공개 조건 완화

`GET /sessions/{id}/result`를 **정답이 아닐 때도** 열어 주십시오. 둘 중 편한 쪽이면 됩니다.

- **A안(선호):** 오답 제출이 `max-wrong-submissions`(기본 3)에 도달하면 세션을 종료 상태로 전이시키고 `result`를 200으로 엽니다.
- **B안:** `POST /api/v1/sessions/{id}/give-up`을 추가해 플레이어가 명시적으로 수사를 종료하면 `result`를 엽니다.

어느 쪽이든 응답 본문은 지금과 동일하면 됩니다. 프론트는 `timeline`을 시간순으로, `alibis`를 인물별로, `solution.requiredEvidenceByRole`을 "정답은 이 증거들이었다"로 렌더링할 예정입니다.

정답으로 끝났는지 아닌지를 프론트가 구분할 수 있도록, 응답에 `resolvedByPlayer: boolean` 한 필드만 더해 주시면 해설 문구를 다르게 쓸 수 있습니다. 없어도 진행에는 지장 없습니다.

## 6. 이번에 요청하지 않는 것

**심문 응답 확장(새 진술 기록 / 연결 가능한 단서 / 모순 가능성 표시)은 이번 요청에서 뺐습니다.**

`NpcTurnResponse.revealedFactIds`가 내부 사실 ID만 담고 있어 프론트가 통째로 버리고 있는 상태이고(`httpApi.ts`의 `sendInterrogationMessage`에서 `revealedEvidenceIds: []`로 고정), 이걸 제대로 살리려면 "어디까지를 모순으로 볼 것인가"에 대한 설계 합의가 먼저 필요합니다. 요청 A~C가 정리된 뒤에 별도 문서로 올리겠습니다.

다만 **요청 A의 `revealedFacts` 형태(`{factId, statement}`)를 심문 응답에도 그대로 쓸 수 있게** 설계해 주시면 나중에 붙이기 쉽습니다.

## 7. 수용 기준

- [ ] `GET /sessions/{id}`의 `discoveredClues[]`에 5개 필드가 추가되고, `linkedClueIds`에 미발견 단서 ID가 절대 섞이지 않는다.
- [ ] `revealedFacts`에 `truthValue`가 포함되지 않는다.
- [ ] `hasPendingConnection`이 대상 단서 ID를 노출하지 않는다.
- [ ] `exclusionsByCharacter` 없이 보낸 기존 요청이 그대로 200을 받는다.
- [ ] 오답 응답의 `missingLogic`에 정답 단서 ID·정답 인물이 포함되지 않는다.
- [ ] 오답으로 끝난 세션에서도 `GET /sessions/{id}/result`가 200을 반환한다.
