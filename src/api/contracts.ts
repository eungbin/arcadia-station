import type { TheoryDraft, TrialResult } from "../store/gameStore";

export type SessionStatus = "PREPARING" | "READY" | "IN_PROGRESS" | "RESULT";

export type SessionDto = {
  sessionId: string;
  status: SessionStatus;
  day: 1 | 2 | 3;
  version: number;
  pollAfterMs?: number;
};

export type InspectObjectResponse = {
  objectId: string;
  discoveredEvidenceIds: string[];
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

export type ApiErrorPayload = {
  code: string;
  message: string;
  retryable: boolean;
};
