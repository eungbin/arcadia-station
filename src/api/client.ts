import type {
  ApiErrorPayload,
  AssistantResponse,
  CompleteDayResponse,
  InspectObjectResponse,
  InterrogationInput,
  InterrogationMessage,
  InterrogationSession,
  SaveTheoryRequest,
  SessionDto,
  TrialVerdictResponse,
} from "./contracts";
import { INVESTIGATION_OBJECTS, NPC_DIALOGUE } from "../data/investigation";
import { resolveMockTrial, type MockCaseId } from "../data/mockVerdict";
import type { TheoryDraft } from "../store/gameStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";
const API_MODE = import.meta.env.VITE_API_MODE ?? "mock";

export class ArcadiaApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly retryable: boolean,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ApiErrorPayload | null;
    throw new ArcadiaApiError(
      payload?.message ?? "서버 요청을 완료하지 못했습니다.",
      payload?.code ?? `HTTP_${response.status}`,
      payload?.retryable ?? response.status >= 500,
    );
  }
  return response.json() as Promise<T>;
}

const mockDelay = (ms = 420) => new Promise((resolve) => window.setTimeout(resolve, ms));
const shouldFail = (target: string) =>
  new URLSearchParams(location.search).get("mockError") === target;
const getMockCase = (): MockCaseId => {
  const requested = new URLSearchParams(location.search).get("mockCase")?.toUpperCase();
  return requested === "MAYA" ||
    requested === "JUNHO" ||
    requested === "SOPHIA" ||
    requested === "KASIM" ||
    requested === "YUNA"
    ? requested
    : "JUNHO";
};
const mockDiscoveredEvidence = new Map<string, Set<string>>();

export type ArcadiaApi = {
  createSession: () => Promise<SessionDto>;
  completeOpening: (sessionId: string) => Promise<SessionDto>;
  inspectObject: (sessionId: string, objectId: string) => Promise<InspectObjectResponse>;
  startInterrogation: (sessionId: string, npcId: string) => Promise<InterrogationSession>;
  sendInterrogationMessage: (
    interrogationId: string,
    input: InterrogationInput,
  ) => Promise<InterrogationMessage>;
  completeDay: (sessionId: string, day: 1 | 2) => Promise<CompleteDayResponse>;
  askAssistant: (
    sessionId: string,
    query: string,
    discoveredEvidenceIds?: string[],
  ) => Promise<AssistantResponse>;
  saveTheory: (sessionId: string, requestBody: SaveTheoryRequest) => Promise<{ version: number }>;
  submitVerdict: (sessionId: string, theory: TheoryDraft) => Promise<TrialVerdictResponse>;
};

const mockApi: ArcadiaApi = {
  async createSession(): Promise<SessionDto> {
    await mockDelay();
    if (shouldFail("session")) {
      throw new ArcadiaApiError("격리 서버와 보안 채널을 열지 못했습니다.", "MOCK_OFFLINE", true);
    }
    const sessionId = `LOCAL-${crypto.randomUUID()}`;
    mockDiscoveredEvidence.set(sessionId, new Set());
    return {
      sessionId,
      status: "READY",
      day: 1,
      version: 1,
    };
  },
  async completeOpening(sessionId: string): Promise<SessionDto> {
    await mockDelay(160);
    return {
      sessionId,
      status: "IN_PROGRESS",
      day: 1,
      version: 2,
    };
  },
  async inspectObject(_sessionId: string, objectId: string): Promise<InspectObjectResponse> {
    await mockDelay(180);
    if (shouldFail("inspect")) {
      throw new ArcadiaApiError("증거 기록 장치가 응답하지 않습니다.", "MOCK_INSPECT_TIMEOUT", true);
    }
    const discovered = mockDiscoveredEvidence.get(_sessionId) ?? new Set<string>();
    discovered.add(objectId);
    mockDiscoveredEvidence.set(_sessionId, discovered);
    return { objectId, discoveredEvidenceIds: [objectId], version: 2 };
  },
  async startInterrogation(
    _sessionId: string,
    npcId: string,
  ): Promise<InterrogationSession> {
    await mockDelay(220);
    if (shouldFail("interrogation")) {
      throw new ArcadiaApiError("심문 채널의 음성 동기화에 실패했습니다.", "MOCK_CHANNEL_LOST", true);
    }
    return {
      interrogationId: `LOCAL-INT-${npcId}`,
      npcId,
      opening: NPC_DIALOGUE[npcId]?.opening ?? "응답이 없습니다.",
      version: 2,
    };
  },
  async sendInterrogationMessage(
    interrogationId: string,
    input: InterrogationInput,
  ): Promise<InterrogationMessage> {
    await mockDelay(360);
    if (shouldFail("interrogation")) {
      throw new ArcadiaApiError("심문 채널이 끊어졌습니다.", "MOCK_CHANNEL_LOST", true);
    }
    const dialogue = NPC_DIALOGUE[input.npcId];
    const choiceResponse = dialogue?.choices.find((choice) => choice.id === input.choiceId)?.response;
    const evidenceTitle = input.evidenceId
      ? input.evidenceId.replaceAll("_", " ")
      : null;
    const freeQuestionResponse = input.query
      ? `“${input.query}”에 대한 제 답은 같습니다. 추측이 아니라 당시 보안 기록과 제 동선으로 판단해 주십시오.`
      : null;
    return {
      interrogationId,
      npcId: input.npcId,
      response:
        choiceResponse ??
        (evidenceTitle
          ? `그 기록은 확인했습니다. 하지만 ${evidenceTitle}만으로 제 행동과 사망을 직접 연결할 수는 없습니다.`
          : freeQuestionResponse ?? "답변할 수 없습니다."),
      revealedEvidenceIds: [],
      version: 3,
    };
  },
  async completeDay(_sessionId: string, day: 1 | 2): Promise<CompleteDayResponse> {
    await mockDelay(300);
    if (shouldFail("day")) {
      throw new ArcadiaApiError("일일 조사 기록을 봉인하지 못했습니다.", "MOCK_SAVE_FAILED", true);
    }
    return { completedDay: day, nextDay: day === 1 ? 2 : 3, version: 4 };
  },
  async askAssistant(
    sessionId: string,
    query: string,
    discoveredEvidenceIds = [],
  ): Promise<AssistantResponse> {
    await mockDelay(520);
    if (shouldFail("assistant")) {
      throw new ArcadiaApiError("수사 보조 연산이 지연되고 있습니다.", "MOCK_AI_TIMEOUT", true);
    }
    const knownIds = new Set([
      ...(mockDiscoveredEvidence.get(sessionId) ?? []),
      ...discoveredEvidenceIds,
    ]);
    const citations = [...knownIds].slice(-3);
    if (citations.length === 0) {
      return {
        summary: "현재 검색할 수 있는 현장 기록이 없습니다.",
        citations: [],
        observation: "먼저 현장 오브젝트를 조사해 로컬 보안 기록에 등록하십시오.",
        suggestedQuery: null,
        fallback: false,
      };
    }
    const labels = citations
      .map((id) => INVESTIGATION_OBJECTS[id]?.title)
      .filter(Boolean)
      .join(", ");
    return {
      summary: `“${query}”와 관련해 현재 공개된 기록 ${citations.length}건을 비교했습니다.`,
      citations,
      observation: `${labels} 사이의 시간·접근 권한·물리 흔적을 함께 검토해야 합니다.`,
      suggestedQuery: "이 기록들 사이에서 시간대가 일치하지 않는 항목은?",
      fallback: false,
    };
  },
  async saveTheory(_sessionId: string, requestBody: SaveTheoryRequest): Promise<{ version: number }> {
    await mockDelay(240);
    if (shouldFail("theory")) {
      throw new ArcadiaApiError("사건 재구성 기록을 저장하지 못했습니다.", "MOCK_SAVE_FAILED", true);
    }
    return { version: requestBody.version + 1 };
  },
  async submitVerdict(_sessionId: string, theory: TheoryDraft): Promise<TrialVerdictResponse> {
    await mockDelay(500);
    if (shouldFail("verdict")) {
      throw new ArcadiaApiError("투표 집계 장치가 응답하지 않습니다.", "MOCK_VOTE_TIMEOUT", true);
    }
    return { ...resolveMockTrial(theory, getMockCase()), version: 6 };
  },
};

const httpApi: ArcadiaApi = {
  createSession: async () => {
    let session = await request<SessionDto>("/sessions", { method: "POST", body: "{}" });
    const deadline = Date.now() + 60_000;
    while (session.status === "PREPARING") {
      if (Date.now() >= deadline) {
        throw new ArcadiaApiError(
          "사건 생성이 제한 시간 안에 완료되지 않았습니다.",
          "SESSION_PREPARATION_TIMEOUT",
          true,
        );
      }
      await mockDelay(Math.max(250, session.pollAfterMs ?? 1_000));
      session = await request<SessionDto>(`/sessions/${session.sessionId}`);
    }
    return session;
  },
  completeOpening: (sessionId: string) =>
    request<SessionDto>(`/sessions/${sessionId}/opening/complete`, {
      method: "POST",
      body: "{}",
    }),
  inspectObject: (sessionId: string, objectId: string) =>
    request<InspectObjectResponse>(`/sessions/${sessionId}/objects/${objectId}/inspect`, {
      method: "POST",
      body: "{}",
    }),
  startInterrogation: (sessionId: string, npcId: string) =>
    request<InterrogationSession>(`/sessions/${sessionId}/interrogations`, {
      method: "POST",
      body: JSON.stringify({ npcId }),
    }),
  sendInterrogationMessage: (interrogationId: string, input: InterrogationInput) =>
    request<InterrogationMessage>(`/interrogations/${interrogationId}/messages`, {
      method: "POST",
      body: JSON.stringify(input),
    }),
  completeDay: (sessionId: string, day: 1 | 2) =>
    request<CompleteDayResponse>(`/sessions/${sessionId}/days/${day}/complete`, {
      method: "POST",
      body: "{}",
    }),
  askAssistant: (sessionId: string, query: string, _discoveredEvidenceIds?: string[]) =>
    request<AssistantResponse>(`/sessions/${sessionId}/assistant`, {
      method: "POST",
      body: JSON.stringify({ query }),
    }),
  saveTheory: (sessionId: string, requestBody: SaveTheoryRequest) =>
    request<{ version: number }>(`/sessions/${sessionId}/theory`, {
      method: "PUT",
      body: JSON.stringify(requestBody),
    }),
  submitVerdict: (sessionId: string, _theory: TheoryDraft) =>
    request<TrialVerdictResponse>(`/sessions/${sessionId}/trial/verdict`, {
      method: "POST",
      body: "{}",
    }),
};

export const arcadiaApi = API_MODE === "http" ? httpApi : mockApi;
