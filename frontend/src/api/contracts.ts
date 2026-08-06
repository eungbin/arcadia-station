import type { TheoryDraft, TrialResult } from "../store/gameStore";

export type SessionStatus = "PREPARING" | "READY" | "IN_PROGRESS" | "RESULT";

export type SessionDto = {
  sessionId: string;
  status: SessionStatus;
  day: 1 | 2 | 3;
  version: number;
  pollAfterMs?: number;
};

/**
 * 사건 생성 진행 단계.
 *
 * AI가 사건을 만드는 데 수십 초에서 2분까지 걸린다. 그 동안 진입 화면이 아무것도 말해 주지
 * 않으면 멈춘 것처럼 보이므로, 백엔드 세션 상태를 그대로 단계로 옮겨 로딩 화면 문구의 근거로
 * 삼는다. 지어낸 진행률이 아니라 실제 서버 상태다.
 */
export type SessionPrepStage = "CREATING" | "VALIDATING" | "READY";

/** 사건 생성 단계가 바뀔 때마다 호출된다. */
export type SessionPrepProgress = (stage: SessionPrepStage) => void;

/** 서버가 판정에 사용하는 단서 종류. */
export type EvidenceType = "PHYSICAL" | "DIGITAL" | "MOTIVE" | "OPPORTUNITY";

/**
 * 서버가 해금해 준 단서 한 건.
 *
 * 수첩·이론·재판이 표시하고 제출하는 증거의 유일한 출처다. 프런트엔드 조사 오브젝트는
 * 3D 소품의 물리적 정체성만 담당하고 증거 내용은 갖지 않는다.
 */
export type DiscoveredEvidence = {
  clueId: string;
  title: string;
  clueType: EvidenceType;
  playerText: string;
  /** 이 단서를 확보한 조사 오브젝트. 수첩에서 출처를 보여주는 용도이며 없을 수 있다. */
  sourceObjectId: string | null;
};

/** 사건의 공개 상태. 새로고침 복구와 진행 중 동기화에 사용한다. */
export type CaseStateResponse = {
  title: string | null;
  briefing: string | null;
  suspectIds: string[];
  evidence: DiscoveredEvidence[];
};

export type InspectObjectResponse = {
  objectId: string;
  /** 이번 조사로 새로 확보한 단서. 없으면 빈 배열이며 오류가 아니다. */
  discoveredEvidence: DiscoveredEvidence[];
  version: number;
};

/**
 * 서버가 제안한 다음 질문.
 *
 * 심문이 한 번 오가면 이어지는 선택지는 이 목록으로 바뀐다. 정적 질문 목록은 사건과 무관하게
 * 고정돼 있어서, 대화가 진행된 뒤에는 방금 들은 답변과 이어지지 않는다.
 */
export type RecommendedQuestion = {
  topicId: string;
  label: string;
};

export type InterrogationMessage = {
  interrogationId: string;
  npcId: string;
  response: string;
  revealedEvidenceIds: string[];
  /** 이 답변에 이어서 물을 수 있는 질문. 비어 있을 수 있다. */
  recommendedQuestions: RecommendedQuestion[];
  version: number;
};

export type InterrogationInput = {
  npcId: string;
  choiceId?: string;
  /** 제시할 서버 단서 ID. */
  evidenceId?: string;
  query?: string;
};

export type InterrogationSession = {
  interrogationId: string;
  npcId: string;
  opening: string;
  version: number;
};

export type CompleteDayResponse = {
  completedDay: 1 | 2;
  nextDay: 2 | 3;
  version: number;
};

export type AssistantResponse = {
  summary: string;
  citations: string[];
  observation: string;
  suggestedQuery: string | null;
  fallback: boolean;
};

export type SaveTheoryRequest = {
  theory: TheoryDraft;
  version: number;
};

export type TrialVerdictResponse = TrialResult & {
  version: number;
};
