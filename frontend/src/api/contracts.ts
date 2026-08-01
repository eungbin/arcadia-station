import type { TheoryDraft, TrialResult } from "../store/gameStore";

export type SessionStatus = "PREPARING" | "READY" | "IN_PROGRESS" | "RESULT";

export type SessionDto = {
  sessionId: string;
  status: SessionStatus;
  day: 1 | 2 | 3;
  version: number;
  pollAfterMs?: number;
};

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

export type InterrogationMessage = {
  interrogationId: string;
  npcId: string;
  response: string;
  revealedEvidenceIds: string[];
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
