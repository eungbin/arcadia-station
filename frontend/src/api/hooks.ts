import { useMutation } from "@tanstack/react-query";
import { arcadiaApi } from "./client";
import { useQuery } from "@tanstack/react-query";
import type {
  InterrogationInput,
  SaveTheoryRequest,
  SessionPrepProgress,
} from "./contracts";
import { useGameStore, type TheoryDraft } from "../store/gameStore";

/**
 * 사건 세션을 만든다.
 *
 * `onProgress`는 AI 사건 생성 단계가 바뀔 때마다 호출된다. 진입 화면의 대기 연출이 실제
 * 서버 상태를 따라가도록 쓰는 통로다.
 */
export function useCreateSession(onProgress?: SessionPrepProgress) {
  return useMutation({
    mutationKey: ["session", "create"],
    mutationFn: async () => {
      const created = await arcadiaApi.createSession(onProgress);
      const session = await arcadiaApi.completeOpening(created.sessionId);
      // 서버가 생성한 사건 개요를 오프닝에서 바로 보여줄 수 있도록 함께 받아 온다.
      const caseState = await arcadiaApi.fetchCaseState(session.sessionId);
      return { session, caseState };
    },
  });
}

/**
 * 사건 개요와 확보한 단서 전체를 서버 기준으로 맞춘다.
 *
 * 조사·검색은 새로 열린 단서만 돌려주는데, 선행 조건이 충족돼 배경에서 열린 단서는 그 응답에
 * 담기지 않는다. 이 조회가 수첩의 기준선을 잡아 준다.
 */
export function useCaseState(sessionId: string | null) {
  const syncCaseState = useGameStore((state) => state.syncCaseState);
  return useQuery({
    queryKey: ["session", sessionId, "case-state"],
    queryFn: async () => {
      const state = await arcadiaApi.fetchCaseState(sessionId!);
      syncCaseState(state);
      return state;
    },
    enabled: Boolean(sessionId),
    staleTime: 5_000,
    retry: false,
  });
}

export function useInspectObject(sessionId: string | null) {
  return useMutation({
    mutationKey: ["session", sessionId, "inspect"],
    mutationFn: (objectId: string) => {
      if (!sessionId) throw new Error("활성 사건 세션이 없습니다.");
      return arcadiaApi.inspectObject(sessionId, objectId);
    },
  });
}

export function useInterrogationSession(sessionId: string | null, npcId: string | null) {
  return useQuery({
    queryKey: ["session", sessionId, "interrogation", npcId],
    queryFn: () => arcadiaApi.startInterrogation(sessionId!, npcId!),
    enabled: Boolean(sessionId && npcId),
    retry: false,
  });
}

export function useSendInterrogationMessage(interrogationId: string | null) {
  return useMutation({
    mutationKey: ["interrogation", interrogationId, "message"],
    mutationFn: (input: InterrogationInput) => {
      if (!interrogationId) throw new Error("활성 심문 채널이 없습니다.");
      return arcadiaApi.sendInterrogationMessage(interrogationId, input);
    },
  });
}

export function useCompleteDay(sessionId: string | null) {
  return useMutation({
    mutationKey: ["session", sessionId, "day", "complete"],
    mutationFn: (day: 1 | 2) => {
      if (!sessionId) throw new Error("활성 사건 세션이 없습니다.");
      return arcadiaApi.completeDay(sessionId, day);
    },
  });
}

export function useAskAssistant(sessionId: string | null) {
  return useMutation({
    mutationKey: ["session", sessionId, "assistant"],
    mutationFn: ({
      query,
      discoveredEvidenceIds,
    }: {
      query: string;
      discoveredEvidenceIds: string[];
    }) => {
      if (!sessionId) throw new Error("활성 사건 세션이 없습니다.");
      return arcadiaApi.askAssistant(sessionId, query, discoveredEvidenceIds);
    },
  });
}

export function useSaveTheory(sessionId: string | null) {
  return useMutation({
    mutationKey: ["session", sessionId, "theory"],
    mutationFn: (requestBody: SaveTheoryRequest) => {
      if (!sessionId) throw new Error("활성 사건 세션이 없습니다.");
      return arcadiaApi.saveTheory(sessionId, requestBody);
    },
  });
}

/**
 * 사건의 전모.
 *
 * 재판이 끝난 뒤에만 열린다. 정답이든 오답 소진이든 볼 수 있고, 아직 끝나지 않은 세션에서는
 * 서버가 409를 돌려주므로 재시도하지 않는다.
 */
export function useFinalReveal(sessionId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ["session", sessionId, "final-reveal"],
    queryFn: () => arcadiaApi.fetchFinalReveal(sessionId!),
    enabled: Boolean(sessionId) && enabled,
    staleTime: Infinity,
    retry: false,
  });
}

export function useSubmitVerdict(sessionId: string | null) {
  return useMutation({
    mutationKey: ["session", sessionId, "verdict"],
    mutationFn: (theory: TheoryDraft) => {
      if (!sessionId) throw new Error("활성 사건 세션이 없습니다.");
      return arcadiaApi.submitVerdict(sessionId, theory);
    },
  });
}
