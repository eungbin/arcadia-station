# 프론트 오브젝트 상호작용 단서 커버리지 전달 문서

> 작성일: 2026-07-31
>
> 대상: 게임 백엔드·프론트엔드·AI 서버 개발자
>
> 프론트 기준 저장소: [`eungbin/arcadia-station`](https://github.com/eungbin/arcadia-station)
>
> 감사 기준 커밋: [`4cff498fb8c1568b4225e9a35f508b2bbf70aa59`](https://github.com/eungbin/arcadia-station/commit/4cff498fb8c1568b4225e9a35f508b2bbf70aa59)

## 1. 요청 요약

프론트 연동 테스트에서 다음 의견이 전달되었습니다.

> 사건에 맞춰 생성되는 단서 중 오브젝트 상호작용으로 발견하는 단서의 수가 더 많았으면 좋겠다.

기존 AI 서버는 장소 단위로만 탐사를 처리하고 프론트가 보내는 `objectHint`를 사용하지 않았습니다. 또한 안전 폴백 사건에는 `EXPLORE` 단서가 1개뿐이어서, 프론트의 여러 조사 오브젝트를 클릭해도 각각의 상호작용에 대응하는 단서를 제공할 수 없었습니다.

이번 변경은 프론트의 실제 오브젝트 ID를 AI 생성 계약에 편입하고, 사건마다 최소 10개 오브젝트 상호작용 단서가 8개 정식 장소 전체에 분포하도록 보장하는 것을 목표로 합니다.

## 2. 프론트 저장소 감사 결과

기준 커밋에서 확인한 프론트 동작은 다음과 같습니다.

- 실제 조사 씬에는 조사 오브젝트 16개와 NPC 5명이 있습니다.
- 16개 오브젝트는 모두 탐사 API 호출 경로를 사용합니다.
- 프론트는 이미 오브젝트 조사 시 `locationId`와 `objectHint`를 함께 전송합니다.
  - 구현 근거: [`src/api/httpApi.ts`](https://github.com/eungbin/arcadia-station/blob/4cff498fb8c1568b4225e9a35f508b2bbf70aa59/src/api/httpApi.ts#L283-L335)
- 프론트는 AI 서버 `8081`을 직접 호출하지 않고, Vite의 `/api` 경로를 통해 게임 백엔드 `8080`을 호출합니다. AI 서버 호출 및 사건 청사진 보관은 게임 백엔드의 책임입니다.
- RAG 질의는 별도 조사 보조 UI이며, 월드 오브젝트 클릭 동작과 분리되어 있습니다.

### 기존 불일치

기존 AI 서버의 `POST /api/v1/sessions/{id}/explore` 요청 DTO에는 `locationId`만 있었기 때문에 프론트가 보낸 `objectHint`가 무시되었습니다. 탐사 서비스 역시 `locationId`만으로 단서를 필터링하여, 같은 방의 첫 오브젝트를 클릭하면 그 방의 `EXPLORE` 단서가 한꺼번에 해금되고 이후 오브젝트에서는 빈 결과가 나올 수 있었습니다.

안전 폴백 사건도 전체 5개 단서 중 `EXPLORE`가 1개, `RAG_QUERY`가 4개인 구성이어서 프론트 피드백이 재현되는 상태였습니다.

## 3. 적용한 해결 내용

### 계약 및 생성 규칙

- 프론트 조사 오브젝트 16개를 모두 정식 `EXPLORE` 오브젝트로 등록했습니다.
- 이 중 10개를 `clueRequired: true`로 지정했습니다.
- 생성된 모든 사건은 필수 오브젝트 10개 각각에 최소 1개의 `EXPLORE` 단서를 가져야 합니다.
- 필수 10개는 아르카디아 스테이션의 정식 장소 8개를 모두 커버합니다.
- 나머지 6개 오브젝트도 유효한 탐사 대상이지만, 모든 사건에서 단서가 반드시 생성되도록 강제하지는 않습니다.
- AI 생성 프롬프트에 전체 오브젝트 로스터, 위치, 필수 단서 여부를 전달합니다.
- 생성 후 서버 교차검증으로 오브젝트 누락, 알 수 없는 오브젝트 ID, 잘못된 위치, 잘못된 소스 타입을 거절합니다.

적용 버전은 다음과 같습니다.

| 계약 | 버전 |
|---|---|
| 추리 규칙 템플릿 | `ARCADIA_MYSTERY_RULES:1.1.0` |
| 사건 생성 프롬프트 | `case-generator-v3` |
| 프론트 통합 계약 | `1.2.0` |

규칙 템플릿의 허용 단서 획득 방식은 실제 런타임 경로가 있는 `EXPLORE`, `RAG_QUERY`, `CONNECT`로 제한했습니다.

### 사건마다 보장되는 오브젝트 10개

| objectHint | locationId | 프론트 의미 | 보장 |
|---|---|---|---|
| `CO_BODY` | `COMMANDER_OFFICE` | 사령관 시신 | 필수 |
| `CO_DOOR_LOG` | `COMMANDER_OFFICE` | 사령관실 출입 기록 | 필수 |
| `CO_XO_PASSAGE` | `DEPUTY_COMMANDER_OFFICE` | 부사령관실 직통 통로 | 필수 |
| `CO_ENV_PANEL` | `CENTRAL_HUB` | 환경 제어 패널 | 필수 |
| `CO_TERMINAL` | `COMMANDER_OFFICE` | 사령관실 터미널 | 필수 |
| `MD_MEDICAL_STORAGE` | `MEDICAL_BAY` | 의무실 보관함 | 필수 |
| `EN_LIFE_SUPPORT` | `ENGINEERING_BAY` | 생명 유지 장치 | 필수 |
| `CM_SECURITY_ARCHIVE` | `COMMUNICATIONS_CENTER` | 보안 기록 보관소 | 필수 |
| `CG_CARGO_MANIFEST` | `CARGO_BAY` | 화물 명세서 | 필수 |
| `CMN_FOOD_STATION` | `COMMON_AREA` | 공용구역 식음 설비 | 필수 |

8개 정식 장소는 `COMMANDER_OFFICE`, `DEPUTY_COMMANDER_OFFICE`, `CENTRAL_HUB`, `MEDICAL_BAY`, `ENGINEERING_BAY`, `COMMUNICATIONS_CENTER`, `CARGO_BAY`, `COMMON_AREA`입니다.

### 필수는 아니지만 정식 등록된 오브젝트 6개

`CO_SCANNER`, `HB_MAINTENANCE`, `XO_RESOURCE_BOARD`, `MD_MEDICAL_TERMINAL`, `CG_AIRLOCK_LOG`, `QT_ACCESS_BUFFER`도 모두 `EXPLORE` 대상으로 등록되어 있습니다. 사건의 내용에 따라 이 오브젝트들에도 추가 단서를 생성할 수 있습니다.

### EXPLORE 단서의 소스 규칙

모든 `EXPLORE` 단서는 다음 규칙을 만족해야 합니다.

1. `clue.source.sourceId`는 프론트 통합 계약 `1.2.0`에 등록된 16개 오브젝트 ID 중 하나여야 합니다.
2. `clue.source.sourceType`은 `PHYSICAL_OBJECT`여야 합니다.
3. `clue.acquisition.locationId`는 해당 `sourceId`에 등록된 `locationId`와 정확히 같아야 합니다.
4. `clueRequired: true`인 10개 오브젝트에는 각각 하나 이상의 `EXPLORE` 단서가 있어야 합니다.

따라서 같은 장소에 여러 단서가 있어도 `objectHint`로 정확한 오브젝트의 단서만 분리하여 반환할 수 있습니다.

### 안전 폴백 사건

AI 생성 실패 시 사용하는 안전 폴백 사건도 총 14개 단서로 확장했습니다.

- `EXPLORE`: 10개 — 위 필수 오브젝트 10개를 모두 커버
- `RAG_QUERY`: 4개

즉 실제 AI 호출이 실패해 폴백으로 전환되어도 오브젝트 중심 탐사 흐름을 그대로 테스트할 수 있습니다.

## 4. 탐사 API 계약

`objectHint`는 선택 필드입니다. 최신 프론트/백엔드 연동에서는 반드시 전달하는 것을 권장합니다.

### 요청 예시

```http
POST /api/v1/sessions/{sessionId}/explore
Content-Type: application/json

{
  "locationId": "ENGINEERING_BAY",
  "objectHint": "EN_LIFE_SUPPORT"
}
```

### 성공 응답 예시

```json
{
  "locationId": "ENGINEERING_BAY",
  "newlyDiscoveredClues": [
    {
      "clueId": "CLUE-ENGINEERING-DIAGNOSTIC",
      "title": "생명 유지 장치 진단 기록",
      "clueType": "DIGITAL",
      "solutionRoles": [],
      "playerText": "생명 유지 장치에 남은 진단 기록을 확인했다."
    }
  ]
}
```

이미 획득한 단서는 중복 지급하지 않으므로 같은 오브젝트를 다시 탐사하면 `newlyDiscoveredClues`가 빈 배열일 수 있습니다.

### 하위 호환 요청

```json
{
  "locationId": "ENGINEERING_BAY"
}
```

`objectHint`를 생략하면 기존과 같이 해당 장소의 획득 가능한 `EXPLORE` 단서를 반환합니다. 이 동작은 구버전 호출자 호환용이며, 오브젝트별 상호작용을 정확히 표현하려면 `objectHint`를 전달해야 합니다.

### 잘못된 위치와 오브젝트 조합

```json
{
  "locationId": "MEDICAL_BAY",
  "objectHint": "EN_LIFE_SUPPORT"
}
```

위 조합은 `EN_LIFE_SUPPORT`의 정식 위치가 `ENGINEERING_BAY`이므로 다음과 같이 `400 Bad Request`로 거절됩니다.

```json
{
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "objectHint does not belong to locationId: EN_LIFE_SUPPORT"
}
```

알 수 없는 `locationId`, 빈 문자열 또는 알 수 없는 `objectHint`도 `400 INVALID_REQUEST`입니다.

## 5. 분리 배포 환경에서 반드시 확인할 경계

이 항목이 가장 중요합니다.

현재 프론트는 게임 백엔드에 `locationId`와 `objectHint`를 이미 함께 보냅니다. 그러나 프론트 → 게임 백엔드 → AI 서버가 분리 배포되어 있다면, 게임 백엔드가 다음 중 하나를 반드시 구현해야 오브젝트별 단서 분리가 실제 서비스에도 적용됩니다.

1. 탐사 요청의 `objectHint`를 AI 서버의 `/explore` 요청에 그대로 전달한다.
2. 게임 백엔드가 동결된 `CaseBlueprint`를 직접 탐사 처리한다면 `acquisition.locationId == locationId`와 함께 `source.sourceId == objectHint`로 필터링한다.

게임 백엔드가 `objectHint`를 DTO에서 버리거나 장소만으로 필터링하면, AI 서버의 생성 품질이 개선되어도 한 오브젝트가 같은 방의 단서를 모두 가져가는 기존 문제가 남습니다. 배포 전 요청 DTO, 내부 클라이언트, 서비스 필터, 통합 테스트까지 연결해서 확인해야 합니다.

## 6. 프론트 후속 권고

프론트 기준 커밋을 감사한 결과, 아래는 후속 정리를 권장합니다.

1. 세션 응답의 `exploreLocationIds`를 실제 맵 활성화 조건으로 사용합니다. 타입에는 선언되어 있지만 현재 렌더링 경로에서 버리고 있어, 서버 로스터와 UI 활성 상태가 어긋날 수 있습니다.
2. `LIFE_SUPPORT_CONTROL`, `LIFE_SUPPORT_CORRIDOR`, `MEDICAL`, `CARGO` 같은 과거 호환용 장소 별칭 재시도를 제거하고 정식 8개 ID만 사용합니다. 현재는 오류를 잡아 다음 별칭으로 재시도하므로 기능이 완전히 중단되지는 않지만, 엄격한 장소 검증에서 불필요한 `400` 요청이 발생합니다.
3. 라이브 통합 테스트에서 “탐사 API 호출 성공”뿐 아니라 서로 다른 필수 오브젝트 10개가 각각 단서를 반환하는지 검증합니다.
4. 같은 장소에 있는 `CO_BODY`, `CO_DOOR_LOG`, `CO_TERMINAL`을 순서대로 클릭했을 때 첫 클릭이 나머지 오브젝트 단서까지 가져오지 않는지 검증합니다.

이번 작업 환경에서 프론트 저장소 권한은 `READ`였으므로 프론트 코드는 수정하거나 푸시하지 않았습니다. 위 항목은 프론트 저장소에서 별도 반영이 필요합니다.

## 7. 테스트 및 배포 전 검증 결과

로컬 JDK 21과 Maven 3.9.16으로 검증했습니다.

- [x] JSON 템플릿 및 샘플 5개 파싱 검증
- [x] 프론트 계약 `1.2.0`의 오브젝트 16개와 `clueRequired` 10개 검증
- [x] 필수 오브젝트 10개가 정식 장소 8개 전체를 커버하는지 검증
- [x] 필수 오브젝트별 단서 누락 시 생성 결과 거절 검증
- [x] 알 수 없는 `sourceId`, 잘못된 `sourceType`, 위치 불일치 거절 검증
- [x] `objectHint`별 단서 분리 반환 통합 테스트
- [x] 위치와 오브젝트 불일치 요청의 `400 INVALID_REQUEST` 검증
- [x] 폴백 사건 14개 단서 구성(`EXPLORE` 10 + `RAG_QUERY` 4) 검증
- [x] 전달용 READY 샘플과 폴백 청사진 구조 일치 검증
- [x] 전달용 READY 샘플 SHA-256 일치 검증
- [x] `mvn test`: 39개 테스트, 실패 0, 오류 0, 건너뜀 0
- [ ] 분리 배포된 게임 백엔드가 `objectHint`를 보존·전달하는지 실제 백엔드와 통합 검증

마지막 미확인 항목은 이 AI 서버 저장소만으로 검증할 수 없는 배포 경계입니다. 게임
백엔드 팀이 DTO와 내부 AI 클라이언트에서 `objectHint`가 유지되는지 확인해야 합니다.

Git 반영 대상은 `agent/npc-interrogation-coverage` 브랜치와 기존 Draft PR
[`tyoonkk/GAME_AI#3`](https://github.com/tyoonkk/GAME_AI/pull/3)입니다.

## 8. 백엔드 개발자에게 복사해 보낼 답변

```text
안녕하세요. 프론트 오브젝트 상호작용 단서 수 확대 요청을 AI 서버에 반영했습니다.

프론트 master 커밋 4cff498fb8c1568b4225e9a35f508b2bbf70aa59 기준으로 조사 동작을 감사했습니다. 프론트는 16개 조사 오브젝트를 클릭할 때 이미 locationId와 objectHint를 함께 보내고 있었습니다. 기존 AI 서버는 objectHint를 받거나 필터에 사용하지 않았고, 폴백 사건에도 EXPLORE 단서가 1개뿐이어서 같은 방의 첫 클릭이 단서를 한꺼번에 가져가거나 대부분의 오브젝트가 빈 결과를 내는 문제가 있었습니다.

이번에 프론트 조사 오브젝트 16개를 모두 정식 EXPLORE 대상으로 등록했고, 그중 아래 10개는 사건마다 최소 1개 단서가 반드시 생성되도록 교차검증을 추가했습니다.

CO_BODY / COMMANDER_OFFICE
CO_DOOR_LOG / COMMANDER_OFFICE
CO_XO_PASSAGE / DEPUTY_COMMANDER_OFFICE
CO_ENV_PANEL / CENTRAL_HUB
CO_TERMINAL / COMMANDER_OFFICE
MD_MEDICAL_STORAGE / MEDICAL_BAY
EN_LIFE_SUPPORT / ENGINEERING_BAY
CM_SECURITY_ARCHIVE / COMMUNICATIONS_CENTER
CG_CARGO_MANIFEST / CARGO_BAY
CMN_FOOD_STATION / COMMON_AREA

이 10개가 정식 장소 8개를 모두 커버합니다. 모든 EXPLORE 단서는 sourceType=PHYSICAL_OBJECT, sourceId=정식 오브젝트 ID, acquisition.locationId=해당 오브젝트의 정식 위치를 만족해야 하며 생성 후 검증기로 강제합니다.

탐사 요청은 다음 형태입니다.
{"locationId":"ENGINEERING_BAY","objectHint":"EN_LIFE_SUPPORT"}

objectHint는 하위 호환을 위해 선택 필드지만, 오브젝트별 단서 분리를 위해 게임 백엔드는 프론트가 보낸 값을 AI 서버까지 그대로 전달해 주세요. 게임 백엔드가 동결된 CaseBlueprint를 직접 처리한다면 locationId와 source.sourceId(objectHint)를 함께 필터링해야 합니다. 위치와 오브젝트가 맞지 않으면 AI 서버는 400 INVALID_REQUEST를 반환합니다.

안전 폴백도 총 14개 단서(EXPLORE 10 + RAG_QUERY 4)로 확장했습니다. 적용 버전은 규칙 ARCADIA_MYSTERY_RULES:1.1.0, 프롬프트 case-generator-v3, 프론트 통합 계약 1.2.0입니다.

특히 분리 배포 환경에서 게임 백엔드 DTO/AI 클라이언트가 objectHint를 버리지 않는지 확인 부탁드립니다. 이 값이 전달되지 않으면 생성 단서 수는 늘어도 오브젝트별 상호작용 분리는 적용되지 않습니다.
```

## 9. 프론트 개발자에게 복사해 보낼 답변

```text
안녕하세요. 말씀 주신 “오브젝트 상호작용으로 얻는 단서가 더 많았으면 좋겠다”는 의견을 AI 사건 생성 계약에 반영했습니다.

프론트 master 커밋 4cff498fb8c1568b4225e9a35f508b2bbf70aa59 기준으로 확인했으며, 현재 구현이 이미 locationId와 objectHint를 함께 보내는 것도 확인했습니다. AI 사건은 이제 프론트의 조사 오브젝트 16개를 모두 정식 EXPLORE 대상으로 알고 생성합니다. 그중 10개 오브젝트에는 매 사건 최소 1개 단서가 보장되고, 이 10개가 맵의 정식 장소 8개를 모두 커버합니다. AI 실패 시 폴백 사건도 EXPLORE 10개와 RAG_QUERY 4개, 총 14개 단서로 확장했습니다.

서버는 objectHint를 기준으로 해당 오브젝트의 단서만 반환하고, locationId와 objectHint의 정식 위치가 다르면 400으로 거절합니다. 같은 사령관실의 시신, 출입 기록, 터미널을 차례로 조사해도 첫 클릭이 나머지 오브젝트 단서까지 가져가지 않도록 분리됩니다.

프론트 후속 작업으로는 세션의 exploreLocationIds를 맵 활성화에 실제 사용하고, 과거 장소 별칭 재시도를 정식 8개 ID로 정리하며, 라이브 테스트에서 필수 오브젝트별 단서 반환 수를 검증하는 것을 권장합니다.

이번 작업 환경의 프론트 저장소 권한은 READ라 프론트 코드는 직접 수정하지 않았습니다. 프론트의 현재 locationId+objectHint 요청 형태는 새 계약과 맞으며, 게임 백엔드가 objectHint를 AI까지 보존해서 전달하는지만 함께 확인해 주세요.
```
