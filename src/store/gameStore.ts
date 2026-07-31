import { create } from "zustand";
import { createJSONStorage, persist, type StateStorage } from "zustand/middleware";
import {
  INVESTIGATION_OBJECTS,
  REQUIRED_SCENE_IDS,
  SUSPECTS,
} from "../data/investigation";
import { validateTheory } from "../domain/theoryValidation";
import type { CaseStateResponse, DiscoveredEvidence } from "../api/contracts";

export type UiLayer =
  | "opening"
  | "playing"
  | "inspection"
  | "interrogation"
  | "notebook"
  | "dayReview"
  | "trial"
  | "result";
export type NotebookTab = "evidence" | "timeline" | "suspects" | "assistant" | "theory";
export type InvestigationPhase = "DAY1" | "DAY2";
/** 백엔드 판정 4역할과 1:1로 대응하는 이론 축. */
export type TheoryEvidenceField = "setup" | "trigger" | "opportunity" | "motive";
export type TrialEnding =
  | "CULPRIT_EXPELLED"
  | "CULPRIT_SURVIVED"
  | "INNOCENT_EXPELLED"
  | "TRIAL_DEADLOCK";

/**
 * 이론 초안. 증거 필드는 모두 서버 단서 ID(`clueId`)이며 백엔드 판정 4역할과 1:1로 맞춘다.
 * 프런트엔드 오브젝트 ID를 넣으면 서버 판정에서 거절된다.
 *
 * 사건에 따라 준비(setup)와 실행(trigger)이 서로 다른 단서라, 두 축을 하나로 합치면
 * 정답 판정이 구조적으로 불가능해진다.
 */
export type TheoryDraft = {
  suspectId: string | null;
  setup: string | null;
  trigger: string | null;
  opportunity: string | null;
  motive: string | null;
  exclusions: Record<string, string>;
};

const EMPTY_THEORY: TheoryDraft = {
  suspectId: null,
  setup: null,
  trigger: null,
  opportunity: null,
  motive: null,
  exclusions: {},
};

export type TrialResult = {
  accusedId: string;
  votesFor: number;
  ending: TrialEnding;
  correctAccusation: boolean;
};

export const resilientLocalStorage: StateStorage = {
  getItem: (name) => {
    const value = localStorage.getItem(name);
    if (!value) return null;
    try {
      JSON.parse(value);
      return value;
    } catch {
      localStorage.removeItem(name);
      return null;
    }
  },
  setItem: (name, value) => localStorage.setItem(name, value),
  removeItem: (name) => localStorage.removeItem(name),
};

type GameState = {
  sessionId: string | null;
  sessionVersion: number;
  layer: UiLayer;
  notebookReturnLayer: "playing" | "result";
  phase: InvestigationPhase;
  notebookTab: NotebookTab;
  focusedId: string | null;
  selectedId: string | null;
  /** 플레이어가 조사한 3D 오브젝트 ID. 진행 게이트와 현장 표시에 쓰는 물리 계층 상태다. */
  discoveredIds: string[];
  /** 서버가 해금해 준 단서. 수첩·이론·재판이 표시하고 제출하는 유일한 증거 출처다. */
  evidence: DiscoveredEvidence[];
  /** 서버가 생성한 사건 개요. */
  caseTitle: string | null;
  caseBriefing: string | null;
  /** 서버가 알려준 이 사건의 용의자 명단. 비어 있으면 정적 로스터로 대체한다. */
  suspectIds: string[];
  interviewedIds: string[];
  theory: TheoryDraft;
  trialResult: TrialResult | null;
  scanUntil: number;
  hasMoved: boolean;
  beginInvestigation: (sessionId?: string, version?: number) => void;
  updateSessionVersion: (version: number) => void;
  setFocused: (id: string | null) => void;
  openInspection: (id: string) => void;
  closeOverlay: () => void;
  toggleNotebook: () => void;
  setNotebookTab: (tab: NotebookTab) => void;
  markInspected: (objectId: string) => void;
  recordEvidence: (found: DiscoveredEvidence[]) => void;
  syncCaseState: (state: CaseStateResponse) => void;
  markInterviewed: (id: string) => void;
  openDayReview: () => void;
  beginDayTwo: () => void;
  setTheorySuspect: (id: string) => void;
  setTheoryEvidence: (field: TheoryEvidenceField, evidenceId: string) => void;
  setTheoryExclusion: (suspectId: string, evidenceId: string) => void;
  startTrial: () => void;
  completeTrial: (result: TrialResult) => void;
  resetSession: () => void;
  activateScan: () => void;
  markMoved: () => void;
};

export const useGameStore = create<GameState>()(persist((set, get) => ({
  sessionId: null,
  sessionVersion: 0,
  layer: "opening",
  notebookReturnLayer: "playing",
  phase: "DAY1",
  notebookTab: "evidence",
  focusedId: null,
  selectedId: null,
  discoveredIds: [],
  evidence: [],
  caseTitle: null,
  caseBriefing: null,
  suspectIds: [],
  interviewedIds: [],
  theory: EMPTY_THEORY,
  trialResult: null,
  scanUntil: 0,
  hasMoved: false,
  beginInvestigation: (sessionId, sessionVersion = 1) =>
    set({ sessionId: sessionId ?? "LOCAL-RESTORED", sessionVersion, layer: "playing" }),
  updateSessionVersion: (sessionVersion) => set({ sessionVersion }),
  setFocused: (focusedId) => {
    if (get().focusedId !== focusedId) set({ focusedId });
  },
  openInspection: (selectedId) => {
    document.exitPointerLock?.();
    set({
      selectedId,
      layer: INVESTIGATION_OBJECTS[selectedId]?.kind === "PERSON" ? "interrogation" : "inspection",
    });
  },
  closeOverlay: () =>
    set((state) => ({
      selectedId: null,
      layer: state.layer === "notebook" ? state.notebookReturnLayer : "playing",
    })),
  toggleNotebook: () => {
    const currentLayer = get().layer;
    const isNotebook = currentLayer === "notebook";
    if (!isNotebook) document.exitPointerLock?.();
    set({
      layer: isNotebook ? get().notebookReturnLayer : "notebook",
      notebookReturnLayer:
        !isNotebook && currentLayer === "result" ? "result" : get().notebookReturnLayer,
      selectedId: null,
    });
  },
  setNotebookTab: (notebookTab) => set({ notebookTab }),
  markInspected: (objectId) => {
    if (
      INVESTIGATION_OBJECTS[objectId]?.kind !== "PERSON" &&
      !get().discoveredIds.includes(objectId)
    ) {
      set((state) => ({ discoveredIds: [...state.discoveredIds, objectId] }));
    }
  },
  recordEvidence: (found) => {
    if (found.length === 0) return;
    set((state) => {
      const known = new Set(state.evidence.map((item) => item.clueId));
      const added = found.filter((item) => !known.has(item.clueId));
      return added.length === 0 ? state : { evidence: [...state.evidence, ...added] };
    });
  },
  // 서버 공개 상태를 기준으로 맞춘다. 새로고침 복구와 배경 해금 반영에 쓴다.
  // 단서는 쌓이기만 하므로 덮어쓰지 않고 합친다. 조회가 잠깐 뒤처져도 기록이 사라지지 않는다.
  syncCaseState: ({ title, briefing, suspectIds, evidence }) =>
    set((state) => {
      const merged = [...state.evidence];
      for (const record of evidence) {
        const index = merged.findIndex((item) => item.clueId === record.clueId);
        if (index === -1) merged.push(record);
        else merged[index] = record;
      }
      return {
        caseTitle: title ?? state.caseTitle,
        caseBriefing: briefing ?? state.caseBriefing,
        suspectIds: suspectIds.length > 0 ? suspectIds : state.suspectIds,
        evidence: merged,
      };
    }),
  markInterviewed: (id) => {
    if (!get().interviewedIds.includes(id)) {
      set((state) => ({ interviewedIds: [...state.interviewedIds, id] }));
    }
  },
  openDayReview: () => {
    const progress = getRequiredProgress(get().discoveredIds);
    if (progress.complete && get().interviewedIds.length >= 3) {
      document.exitPointerLock?.();
      set({ layer: "dayReview" });
    }
  },
  beginDayTwo: () => set({ phase: "DAY2", layer: "playing" }),
  setTheorySuspect: (suspectId) =>
    set((state) => ({
      theory: { ...state.theory, suspectId, exclusions: {} },
    })),
  setTheoryEvidence: (field, evidenceId) =>
    set((state) => ({ theory: { ...state.theory, [field]: evidenceId } })),
  setTheoryExclusion: (suspectId, evidenceId) =>
    set((state) => ({
      theory: {
        ...state.theory,
        exclusions: { ...state.theory.exclusions, [suspectId]: evidenceId },
      },
    })),
  startTrial: () => {
    const { theory, phase, evidence, suspectIds } = get();
    const validation = validateTheory(
      theory,
      evidence.map((item) => item.clueId),
      suspectIds.length > 0 ? suspectIds : SUSPECTS.map((suspect) => suspect.id),
    );
    if (phase === "DAY2" && validation.valid) {
      document.exitPointerLock?.();
      set({ layer: "trial" });
    }
  },
  completeTrial: (trialResult) => set({ trialResult, layer: "result" }),
  resetSession: () =>
    set({
      sessionId: null,
      sessionVersion: 0,
      layer: "opening",
      notebookReturnLayer: "playing",
      phase: "DAY1",
      notebookTab: "evidence",
      focusedId: null,
      selectedId: null,
      discoveredIds: [],
      evidence: [],
      caseTitle: null,
      caseBriefing: null,
      suspectIds: [],
      interviewedIds: [],
      theory: EMPTY_THEORY,
      trialResult: null,
      scanUntil: 0,
      hasMoved: false,
    }),
  activateScan: () => set({ scanUntil: performance.now() + 3_000 }),
  markMoved: () => {
    if (!get().hasMoved) set({ hasMoved: true });
  },
}), {
  name: "arcadia-station-session-v1",
  storage: createJSONStorage(() => resilientLocalStorage),
  partialize: (state) => ({
    sessionId: state.sessionId,
    sessionVersion: state.sessionVersion,
    layer:
      state.layer === "result"
        ? "result"
        : state.layer === "opening"
          ? "opening"
          : "playing",
    notebookReturnLayer: "playing",
    phase: state.phase,
    notebookTab: state.notebookTab,
    focusedId: null,
    selectedId: null,
    discoveredIds: state.discoveredIds,
    evidence: state.evidence,
    caseTitle: state.caseTitle,
    caseBriefing: state.caseBriefing,
    suspectIds: state.suspectIds,
    interviewedIds: state.interviewedIds,
    theory: state.theory,
    trialResult: state.trialResult,
    scanUntil: 0,
    hasMoved: state.hasMoved,
  }),
  version: 2,
  migrate: (persistedState, fromVersion) => {
    const state = persistedState as Partial<GameState>;
    return {
      ...state,
      sessionVersion: typeof state.sessionVersion === "number" ? state.sessionVersion : 1,
      // v1은 증거를 프런트엔드 오브젝트 ID로 저장했다. 그 ID는 서버 판정에서 거절되므로
      // 증거와 이론을 비우고 서버 공개 상태로 다시 채운다.
      evidence: fromVersion < 2 ? [] : (state.evidence ?? []),
      caseTitle: fromVersion < 2 ? null : (state.caseTitle ?? null),
      caseBriefing: fromVersion < 2 ? null : (state.caseBriefing ?? null),
      suspectIds: fromVersion < 2 ? [] : (state.suspectIds ?? []),
      theory:
        fromVersion < 2
          ? EMPTY_THEORY
          : state.theory,
    } as GameState;
  },
}));

export function getRequiredProgress(discoveredIds: string[]) {
  const found = REQUIRED_SCENE_IDS.filter((id) => discoveredIds.includes(id)).length;
  return { found, total: REQUIRED_SCENE_IDS.length, complete: found === REQUIRED_SCENE_IDS.length };
}
