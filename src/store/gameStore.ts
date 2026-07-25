import { create } from "zustand";
import { createJSONStorage, persist, type StateStorage } from "zustand/middleware";
import {
  INVESTIGATION_OBJECTS,
  REQUIRED_SCENE_IDS,
  SUSPECTS,
} from "../data/investigation";
import { validateTheory } from "../domain/theoryValidation";

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
export type TheoryEvidenceField = "method" | "motive" | "trace";
export type TrialEnding =
  | "CULPRIT_EXPELLED"
  | "CULPRIT_SURVIVED"
  | "INNOCENT_EXPELLED"
  | "TRIAL_DEADLOCK";

export type TheoryDraft = {
  suspectId: string | null;
  method: string | null;
  motive: string | null;
  trace: string | null;
  exclusions: Record<string, string>;
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
  discoveredIds: string[];
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
  recordEvidence: (id: string) => void;
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
  interviewedIds: [],
  theory: {
    suspectId: null,
    method: null,
    motive: null,
    trace: null,
    exclusions: {},
  },
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
  recordEvidence: (id) => {
    if (
      INVESTIGATION_OBJECTS[id]?.kind !== "PERSON" &&
      !get().discoveredIds.includes(id)
    ) {
      set((state) => ({ discoveredIds: [...state.discoveredIds, id] }));
    }
  },
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
    const { theory, phase, discoveredIds } = get();
    const validation = validateTheory(
      theory,
      discoveredIds,
      SUSPECTS.map((suspect) => suspect.id),
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
      interviewedIds: [],
      theory: {
        suspectId: null,
        method: null,
        motive: null,
        trace: null,
        exclusions: {},
      },
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
    interviewedIds: state.interviewedIds,
    theory: state.theory,
    trialResult: state.trialResult,
    scanUntil: 0,
    hasMoved: state.hasMoved,
  }),
  version: 1,
  migrate: (persistedState) => {
    const state = persistedState as Partial<GameState>;
    return {
      ...state,
      sessionVersion: typeof state.sessionVersion === "number" ? state.sessionVersion : 1,
    } as GameState;
  },
}));

export function getRequiredProgress(discoveredIds: string[]) {
  const found = REQUIRED_SCENE_IDS.filter((id) => discoveredIds.includes(id)).length;
  return { found, total: REQUIRED_SCENE_IDS.length, complete: found === REQUIRED_SCENE_IDS.length };
}
