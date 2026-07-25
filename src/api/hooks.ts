import { useMutation } from "@tanstack/react-query";
import { arcadiaApi } from "./client";
import { useQuery } from "@tanstack/react-query";
import type { InterrogationInput, SaveTheoryRequest } from "./contracts";
import type { TheoryDraft } from "../store/gameStore";

export function useCreateSession() {
  return useMutation({
    mutationKey: ["session", "create"],
    mutationFn: async () => {
      const session = await arcadiaApi.createSession();
      return arcadiaApi.completeOpening(session.sessionId);
    },
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

export function useSubmitVerdict(sessionId: string | null) {
  return useMutation({
    mutationKey: ["session", sessionId, "verdict"],
    mutationFn: (theory: TheoryDraft) => {
      if (!sessionId) throw new Error("활성 사건 세션이 없습니다.");
      return arcadiaApi.submitVerdict(sessionId, theory);
    },
  });
}
