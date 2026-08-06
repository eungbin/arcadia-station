# AI 플레이 경험 개선 반영 및 프론트 연동 요청

작성일: 2026-08-06
대상: AI 서버, 게임 백엔드, 프론트엔드 공동 확인

## 결론

AI 서버가 담당하는 세 가지는 반영했다.

1. 심문은 같은 세션·같은 NPC의 최근 대화 8턴을 기억하고 다음 답변의 문맥으로 사용한다.
2. 매 심문 턴 뒤에는 이전 질문·제시 증거·공개 사실에 맞춘 안전한 후속 질문 후보를 만든다.
3. 수사 보조와 NPC 화면용 문장에서 내부 ID, 영문 명령 코드, 원본 metadata가 보이지 않도록 생성·검증·표시 단계를 보강했다.

다만 **첫 인사와 실제 화면의 질문 버튼 교체는 현재 프론트의 정적 UI가 담당하고 있어 프론트 변경 없이는 화면에 나타나지 않는다.** AI 서버만 수정해도 첫 대사는 바뀌지 않으며, 반환된 `recommendedQuestions`도 현재 프론트가 버리고 있다. 아래의 프론트 연동 요청을 반드시 함께 반영해야 사용자 피드백 전체가 완료된다.

## 피드백별 처리 상태

| 피드백 | 처리 상태 | 담당 |
|---|---|---|
| 첫 대사가 사건 사실부터 말해 어색함 | 정적 첫 인사 교체 필요 | 프론트 |
| 선택지 3개가 계속 고정됨 | AI가 매 턴 다른 후속 질문 2개를 반환하도록 완료. UI 표시 연결 필요 | AI 완료 / 프론트 연결 필요 |
| 직접 질문 후에도 맞춤형 선택지가 필요 | 최근 대화·이번 제시 증거를 반영한 후보 생성 완료 | AI 완료 / 프론트 연결 필요 |
| 수사 보조에 `SOPHIA`, `CENTRAL_HUB`, `RUN_SAFETY_DIAGNOSTIC` 등이 보임 | 공개 단서 문장 기반 RAG, 한국어 표시 정리, fallback 정리 완료 | AI 완료 |
| 증거 문장이 코드 중심이라 읽기 어려움 | 생성 프롬프트와 CaseBlueprint 검증기에 플레이어 문장 코드 금지 규칙 추가 | AI 완료 |
| NPC가 이전 대화를 기억하지 못함 | 세션·NPC별 최근 8개 검증 완료 문답을 다음 AI 프롬프트에 전달 | AI 완료 |
| 카운트다운→단계, 생성 대기, 수첩 가이드, 목표 패널, 시작 전 사양 변경 | 프론트 팀이 반영했다고 공유한 항목. 이번 AI 변경 범위에는 포함하지 않음 | 프론트 |

## AI 서버 반영 내용

### 1. 안전한 NPC 대화 메모리

- `NpcConversationMemory`가 `(sessionId, characterId)`별 최근 **8턴**을 보관한다.
- 모델에 전달되는 것은 이전에 서버 검증을 통과한 `질문 / 대사 / 감정 / 제시 단서 / 공개 사실`뿐이다.
- 같은 NPC의 동시 요청은 순서가 섞이지 않도록 직렬 처리한다.
- 질문은 최대 240자로 제한해 대화 이력으로 인한 프롬프트 비대화를 막는다.
- 서버 재시작 또는 여러 AI 서버 인스턴스 사이에는 메모리가 공유되지 않는다. 현재 세션 자체가 AI 서버 인메모리 기반이므로 같은 제약이다.

외부 제공자의 ChatMemory에 의존하지 않았다. Gemini/OpenAI를 바꿔도 같은 화이트리스트·사실 공개 규칙을 적용하기 위해 서버가 대화 이력을 직접 통제한다.

### 2. 상황형 후속 질문

첫 턴에는 사건의 NPC 지식에서 만든 기본 주제를 제공한다. 그 다음부터는 다음 우선순위로 후보를 만든다.

1. 방금 제시한 기록과 진술의 관계
2. 직전 답변의 시간 순서 재확인
3. 공개 사실이 있으면 세부 확인, 없으면 뒷받침 기록 확인
4. 아직 묻지 않은 기본 주제

모델은 이 서버 후보 중 정확히 2개만 고를 수 있고, `topicId`와 `label`을 임의로 바꾸면 응답이 안전 fallback으로 대체된다. 따라서 버튼 문구로 미공개 사실이 새는 문제를 막는다.

응답 계약은 기존과 같다.

```json
{
  "dialogue": "...",
  "emotion": "CALM",
  "revealedFactIds": [],
  "recommendedQuestions": [
    { "topicId": "FOLLOW_UP_EVIDENCE", "label": "방금 제시한 기록과 당신의 진술이 어떻게 양립하는지 설명해 주십시오." },
    { "topicId": "FOLLOW_UP_TIMELINE", "label": "방금 답변을 시간 순서대로 다시 설명해 주십시오." }
  ]
}
```

게임 백엔드는 이미 이 배열을 DTO로 그대로 전달한다. 새 API나 백엔드 DTO 변경은 필요 없다.

### 3. 플레이어용 수사 보조·증거 표현

- RAG 모델에는 원본 `recordId`, metadata, 시스템 원문 대신 연결된 단서의 `playerText`만 전달한다.
- 수사 보조의 fallback도 원문 로그를 이어 붙이지 않고 `핵심 정리:` 형식의 짧은 한국어 목록을 만든다.
- `RUN_SAFETY_DIAGNOSTIC`, `CENTRAL_HUB`, `SOPHIA`, `FACT-TRIGGER`, `CLUE-TRIGGER-LOG`, `REC_001` 등은 표시 직전에 한국어 표현 또는 중립적인 `해당 기록`으로 정리한다.
- NPC AI 대사에도 같은 표시 정리를 적용한다.
- 새 사건 생성 프롬프트는 화면용 문장에 ID·enum·명령 코드·metadata를 쓰지 말도록 요구한다.
- `PlayerFacingNarrativeCheck`가 코드·하이픈 ID·정식 로스터 ID를 발견하면 해당 AI 사건을 검증 실패로 처리하고 재생성한다.

사건 생성 프롬프트 버전은 `case-generator-v4`다. seed마다 관찰 문구, 알리바이 충돌, 동기, 오인 단서의 조합을 다르게 만들도록 지시도 강화했다. 외부 API 실패 뒤의 fallback은 의도적으로 고정된 안전 사건이므로, fallback 상태에서는 단서가 반복되는 것이 정상이다.

## 프론트 필수 연동 요청

### A. 첫 인사

현재 `frontend/src/api/httpApi.ts`의 `startInterrogation()`은 API 호출 없이 `NPC_DIALOGUE[npcId].opening`을 그대로 사용한다. 첫 인사는 정적 UI 문구로 두는 편이 비용·지연 없이 자연스럽다.

`frontend/src/data/investigation.ts`의 각 NPC `opening`을 사건의 결론을 미리 말하지 않는 성격형 인사로 교체해 달라. 예시는 아래와 같다.

| NPC | 예시 첫 인사 |
|---|---|
| 소피아 | “안녕하세요. 지금은 모두 예민한 상황이군요. 제가 아는 범위에서 차분히 답하겠습니다.” |
| 마야 | “왔군요. 혼란스러운 건 알지만, 확인이 필요한 게 있으면 순서대로 물어보세요.” |
| 백준호 | “정비 기록은 정리해 두었습니다. 필요한 부분부터 말씀하세요.” |
| 카심 | “통신 채널은 안정적입니다. 질문이 있다면 사실대로 답하겠습니다.” |
| 유나 | “화물 관련 기록도 확인 중입니다. 궁금한 점이 있으면 물어보세요.” |

AI가 첫 인사까지 매번 생성하도록 바꾸려면 새 opening API와 로딩 상태가 필요하다. 현재는 정적 성격형 인사가 더 적합하다.

### B. 고정 버튼 대신 `recommendedQuestions` 표시

현재 프론트는 `BackendNpcTurn.recommendedQuestions` 타입을 이미 알고 있지만, `httpApi.ts`의 `sendInterrogationMessage()` 반환값에서 버리고 `GameUI.tsx`가 `NPC_DIALOGUE.choices` 세 개만 계속 그린다.

프론트 작업 순서:

1. `InterrogationMessage`에 `recommendedQuestions`를 보관한다.
2. `InterrogationPanel`에 `currentChoices` 상태를 추가하고, 시작 시에는 정적 `NPC_DIALOGUE.choices`를 fallback으로 사용한다.
3. 심문 성공 응답을 받으면 `currentChoices`를 `message.recommendedQuestions`로 교체한다.
4. 동적 버튼을 눌렀을 때에는 정적 `choiceId`가 아니라 `query: choice.label`로 기존 심문 API를 호출한다.
5. 응답이 비었거나 API가 실패한 경우에만 기존 정적 선택지로 유지한다.

AI는 2개의 안전한 추천 질문을 반환하고, 기존 직접 질문 입력창이 세 번째 자유 질문 역할을 유지한다. 화면 디자인상 항상 세 개의 버튼이 필요하다면 세 번째는 `직접 질문`으로 두는 것을 권장한다. AI가 3개를 임의 생성하게 늘리면 미공개 사실 노출 범위와 백엔드 Fake fallback도 함께 변경해야 한다.

## 백엔드 확인 사항

백엔드는 이번 기능을 위해 API/DTO를 바꿀 필요가 없다. 다음만 유지하면 된다.

- AI 응답의 `recommendedQuestions` 배열을 프론트 응답까지 보존한다.
- 기존 `revealedFactIds` 화이트리스트 재검증을 계속 적용한다.
- AI 서버 재시작 등으로 심문 세션이 유실되면 현재의 안전 fallback을 사용한다.

## 테스트 결과

아래는 외부 API 비용 없이 실행한 AI 서버 테스트다.

결과: **59 tests, 0 failures, 0 errors**.

```powershell
cd <arcadia-station 경로>\ai-server
.\mvnw.cmd test
```

추가·확인한 항목:

- 세션·NPC별 대화 메모리 격리와 최근 8턴 제한
- 첫 질문과 다음 턴의 후속 질문 후보가 달라지는지
- NPC 추천 질문의 서버 후보 화이트리스트 검증
- RAG fallback이 내부 코드 없이 `핵심 정리:`를 반환하는지
- 코드/하이픈 ID/정식 로스터 ID가 화면용 사건 문장에 들어가면 생성 검증이 실패하는지
- 기존 사건 생성, NPC 커버리지, 8개 장소, 오브젝트 단서, 내부 계약 스냅샷 회귀 테스트

실제 Gemini 호출은 비용이 발생하므로 별도로 아래 스모크 테스트로 확인한다.

```powershell
.\scripts\test-ai-case-generation.cmd -Mode gemini
```

성공 기준은 아래 두 로그가 함께 보이는 것이다.

```text
[AI-API][SUCCESS] ... purpose=CASE_GENERATION ... httpStatus=200
[AI-CASE][RESULT] ... mode=API generationSource=AI fallbackReason=NONE
```

## 다음 우선순위

1. **프론트:** 첫 인사 교체와 `recommendedQuestions` 화면 연결
2. **공동 실제 테스트:** 실제 Gemini 사건 생성 → 프론트에서 NPC 2회 심문 → 수사 보조 검색 → 코드 노출/후속 버튼/대화 문맥 확인
3. **AI 후속 개선:** 모델 대사 문장 자체가 허용 사실에서 벗어나지 않는지 더 강하게 검사하는 fact-grounding 규칙
4. **배포 확장 시:** AI 서버가 여러 인스턴스가 되면 Redis/백엔드 보관 방식의 세션 대화 이력으로 전환
