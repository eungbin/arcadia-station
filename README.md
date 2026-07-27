# ARCADIA // INCIDENT 72

아르카디아 스테이션 살인 사건을 조사하는 데스크톱 웹 기반 1인칭 3D 추리게임 프런트엔드입니다.

## 실행

요구 사항:

- Node.js 22 이상
- npm 11 이상

```bash
npm install
npm run dev
```

기본 개발 주소는 `http://127.0.0.1:5173`입니다.

로컬 게임 백엔드와 연결할 때는 `.env.example`을 `.env.local`로 복사하고 다음 값을
사용합니다.

```dotenv
VITE_API_MODE=http
VITE_API_BASE_URL=/api
VITE_API_PROXY_TARGET=http://127.0.0.1:8080
```

Vite가 `/api` 요청을 게임 백엔드로 프록시하며 브라우저가 AI 서버나 AI 내부 키에
직접 접근하지 않습니다.

검증:

```bash
npm run verify
npm run test:browser
```

## 조작

| 입력 | 동작 |
|---|---|
| `WASD` | 이동 |
| 마우스 | 시점 회전 |
| `E` | 오브젝트 조사·용의자 심문 |
| `Q` | 3초간 조사 대상 스캔 |
| `Tab` | 사건 수첩 |
| `Esc` | 현재 오버레이 닫기 |

3D 화면을 클릭하면 마우스 시점 조작이 활성화됩니다.
터치 환경에서는 화면 왼쪽 이동 패드, 오른쪽 드래그 시점, 조사·스캔·수첩 버튼이 표시됩니다.

## 현재 구현

- 태양풍 격리·사령관 사망 오프닝
- Rapier 기반 1인칭 이동과 정적 충돌
- 중앙 허브, 사령관실, 부사령관실과 직통 통로
- 의무실, 엔지니어링, 통신실, 화물·도킹, 식당·라운지, 승무원 숙소
- 구역별 조명·재질·표지·환경 소품
- 사령관실 핵심 현장 오브젝트 조사
- 전문 구역의 D2 로그·실물 증거 조사
- 다섯 용의자 3D 배치와 1차 심문
- 질문 선택, 증거 제시와 진술 기록
- 증거·타임라인·용의자·수사 보조·사건 재구성 수첩
- D1 완료 조건과 D2 심층 조사 전환
- 범인·수단·동기·흔적·비범인 배제 근거 구성
- 5단계 생존자 재판과 투표
- 정답, 입증 실패, 오답 추방, 재판 결렬 엔딩
- 새로고침 진행 복구와 새 사건 초기화
- 데스크톱·모바일 반응형 UI와 터치 조작
- 전역 오류 복구, 손상된 저장 데이터 자동 격리, API 오류별 재시도·정적 폴백
- 초기 화면과 3D 지연 청크의 gzip 성능 예산 검사

## 데이터 경계

기본값인 `VITE_API_MODE=mock`에서는 세션 생성부터 조사, 심문, 일차 종료, 수사 보조, 이론 저장과 판정까지 브라우저 내부 어댑터로 동작합니다. `VITE_API_MODE=http`로 바꾸면 같은 UI와 상태 흐름이 REST API를 사용합니다.

실제 연동 시 다음 부분을 교체합니다.

- 사건 생성 대기 → `POST /api/sessions`
- 공개 진행 상태 → `GET /api/sessions/{id}`
- 오브젝트 조사·분석 → 세션 조사 API
- NPC 심문 → 심문 API
- 발견 기록 기반 수사 보조 → 세션 보조 API
- D1/D2 기록 봉인 → 일차 완료 API
- 이론 제출·재판 → 서버 판정 API

프런트엔드는 운영 환경에서 범인 ID나 비공개 `CaseBible`을 받지 않습니다.
구체적인 요청·응답과 오류 규약은 [`API_INTEGRATION.md`](./docs/API_INTEGRATION.md)에 정리되어 있습니다.
게임 백엔드는 `tyoonkk/GAME_AI`의 사건 생성 계약과 공유 ID 변환표를 통해 AI 서버를
연결합니다.

개발용 오류 화면은 URL의 `mockError`로 재현할 수 있습니다.

```text
?mockError=session
?mockError=inspect
?mockError=interrogation
?mockError=assistant
?mockError=day
?mockError=theory
?mockError=verdict
```

재판 폴백 사건은 `?mockCase=maya|junho|sophia|kasim|yuna`로 재현합니다. 이 선택 기능은 mock 모드에만 존재합니다.

## 주요 경로

```text
src/
├─ data/                 # 공개 조사 데이터와 개발용 판정 어댑터
├─ game/
│  ├─ ArcadiaScene.tsx   # Canvas, 조명, 물리, 후처리
│  ├─ PlayerController.tsx
│  ├─ InteractionController.tsx
│  └─ world/
│     └─ StationWorld.tsx
├─ store/
│  └─ gameStore.ts       # 진행·수첩·재판 상태와 로컬 복구
└─ ui/
   ├─ GameUI.tsx         # 오프닝, HUD, 조사, 심문, 수첩, 재판, 결과
   ├─ MobileControls.tsx # 터치 이동·시점·게임 액션
   └─ AppErrorBoundary.tsx
```

세계관과 구현 기준은 [`DEVELOPMENT_SPEC.md`](./docs/DEVELOPMENT_SPEC.md)를 따릅니다.
