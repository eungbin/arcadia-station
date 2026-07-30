# AI 서버팀 전달용 — 탐사 장소(locationId) 정식 로스터 요청

> 작성일: 2026-07-31
>
> 관련 선례: `docs/ai-server-integration-response.md` (NPC 심문 대상 커버리지 요청과 같은 종류의 문제)

## 배경

프론트에서 아르카디아 스테이션의 고정 맵 UI를 이미 설계해뒀습니다. 구조는 다음과 같습니다.

```
사령관실(범행 현장·출입기록) ─ 부사령관 집무실(헨드릭스 전용·직통)
              └────────┬────────┘
          중앙 허브(복도) — 환경 제어 패널 포함
   ┌────────┬─────────┬────────┬────────┐
 의무실   엔지니어링   통신실    화물칸    공용구역
(소피아)  (백준호)    (카심)  (유나·에어록) (식당·숙소)
```

플레이어는 이 맵 전체를 자유롭게 이동하며 각 방을 탐사(`POST /api/v1/sessions/{id}/explore`)할 수 있어야 합니다.

## 문제

`caseBlueprint.clues[].acquisition.locationId`가 이 8개 방 구조를 따르는 고정 로스터가 아니라, 사건마다 즉흥적으로 생성되는 값처럼 보입니다. 저희가 갖고 있는 두 샘플을 비교해보니:

| 소스 | locationId 값 | 개수 |
|---|---|---|
| 로컬 Fake 픽스처 (`src/main/resources/fixtures/sample-case-blueprint.json`) | `MEDICAL_BAY`, `LIFE_SUPPORT_CORRIDOR`, `PERSONAL_QUARTERS` | 3개 |
| AI 서버 실제 생성 샘플 (`src/test/resources/ai-server/internal-case-ready.response.json`) | `COMMAND_DECK`, `LIFE_SUPPORT_CONTROL` | 2개 |

두 샘플 다 위 8개 방 구조와 이름·개수가 맞지 않고, 서로 간에도 겹치는 값이 없습니다. `characterId`가 `worldTemplate.characters[].id`라는 정식 로스터를 참조하는 것과 달리, 장소는 아직 그런 고정 로스터를 참조하지 않는 것으로 보입니다.

이건 앞서 확인했던 NPC 심문 커버리지 문제(`alibis`는 5명인데 `npcKnowledge`는 1명뿐이었던 것)와 같은 계열의 문제입니다 — 프론트가 참조할 "정식 값 목록"이 AI 서버 쪽에서 아직 고정되지 않은 상태입니다.

## 요청

1. 위 8개 방을 `worldTemplate.locations[].id`(또는 동등한 필드)로 정식 로스터화해주세요. 프론트 맵 UI가 이미 이 구조로 설계돼 있어서, 가능하면 이 방 이름·구성을 그대로 반영해주시면 좋겠습니다.
2. 사건 생성 시 `clues[].acquisition.locationId`, `timeline[].locationId`, `method.setupAction/triggerAction.locationId` 등 장소를 참조하는 모든 필드가 이 정식 로스터 안의 값만 쓰도록, `characterId` 때 추가하신 것과 같은 종류의 교차검증(`InterrogationCoverageCheck`류)을 장소에도 적용해주세요.
3. 확정된 로스터 값 목록(ID 문자열 기준)을 공유해주시면, 저희는 이미 구현해둔 `exploreLocationIds`(3번 API 응답, 매 세션마다 사건에 실제로 존재하는 탐사 가능 장소를 동적으로 내려주는 필드)를 통해 프론트에 그대로 전달하겠습니다. 프론트는 이 ID를 받아 자체 맵 좌표/라벨 테이블에 매핑하는 방식으로 렌더링하면 되고, ID 값 자체를 하드코딩하지는 않을 예정입니다.

`characterId` 때와 마찬가지로, 저희 쪽 엔드포인트나 DTO 구조는 변경할 필요가 없고 장소 로스터가 확정되는 대로 자동으로 반영됩니다.

확인 부탁드립니다.
