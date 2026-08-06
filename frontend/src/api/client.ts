import type {
  AssistantResponse,
  CaseStateResponse,
  CompleteDayResponse,
  DiscoveredEvidence,
  InspectObjectResponse,
  InterrogationInput,
  InterrogationMessage,
  InterrogationSession,
  SaveTheoryRequest,
  SessionDto,
  SessionPrepProgress,
  TrialVerdictResponse,
} from "./contracts";
import { INVESTIGATION_OBJECTS, NPC_DIALOGUE, SUSPECTS } from "../data/investigation";
import { resolveMockTrial, type MockCaseId } from "../data/mockVerdict";
import type { TheoryDraft } from "../store/gameStore";
import { ArcadiaApiError } from "./errors";
import { httpApi } from "./httpApi";

export { ArcadiaApiError };

const API_MODE = import.meta.env.VITE_API_MODE ?? "mock";

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
const mockDiscoveredEvidence = new Map<string, DiscoveredEvidence[]>();

/**
 * mock 모드용 단서. HTTP 모드와 같은 모양의 서버 단서를 정적 조사 데이터에서 만들어,
 * UI가 두 모드에서 동일한 지식 계층을 보게 한다.
 */
const MOCK_CLUE_PREFIX = "MOCK-";

/** mock 심문이 돌려주는 후속 질문. 실제 백엔드의 `recommendedQuestions`와 같은 모양이다. */
const MOCK_RECOMMENDED_QUESTIONS = [
  { topicId: "TOPIC-WHEREABOUTS", label: "그 시간에 어느 구역에 있었습니까?" },
  { topicId: "TOPIC-ACCESS", label: "사령관실 접근 권한은 누구에게 있었습니까?" },
  { topicId: "TOPIC-LAST-CONTACT", label: "사령관과 마지막으로 나눈 대화는 무엇이었습니까?" },
];

function mockEvidenceFor(objectId: string): DiscoveredEvidence | null {
  const object = INVESTIGATION_OBJECTS[objectId];
  if (!object || object.kind === "PERSON") return null;
  return {
    clueId: `${MOCK_CLUE_PREFIX}${objectId}`,
    title: object.evidenceLabel,
    clueType: object.kind === "WORLD" ? "OPPORTUNITY" : object.kind,
    playerText: object.detail,
    sourceObjectId: objectId,
  };
}

export type ArcadiaApi = {
  /** `onProgress`는 사건 생성 단계가 바뀔 때마다 호출된다. 로딩 화면 문구의 근거다. */
  createSession: (onProgress?: SessionPrepProgress) => Promise<SessionDto>;
  completeOpening: (sessionId: string) => Promise<SessionDto>;
  /** 사건 개요와 지금까지 확보한 단서 전체. 새로고침 복구와 진행 중 동기화에 쓴다. */
  fetchCaseState: (sessionId: string) => Promise<CaseStateResponse>;
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
  async createSession(onProgress): Promise<SessionDto> {
    // 실제 AI 사건 생성은 수십 초가 걸린다. mock은 빨라야 개발이 편하지만 그러면 생성 대기
    // 화면을 볼 수가 없어서, `?mockSlowCase=1`로 실제에 가까운 길이를 재현할 수 있게 둔다.
    const stageMs = new URLSearchParams(location.search).has("mockSlowCase") ? 6_000 : 420;
    onProgress?.("CREATING");
    await mockDelay(stageMs);
    if (shouldFail("session")) {
      throw new ArcadiaApiError("격리 서버와 보안 채널을 열지 못했습니다.", "MOCK_OFFLINE", true);
    }
    onProgress?.("VALIDATING");
    await mockDelay(stageMs);
    onProgress?.("READY");
    const sessionId = `LOCAL-${crypto.randomUUID()}`;
    mockDiscoveredEvidence.set(sessionId, []);
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
  async fetchCaseState(sessionId: string): Promise<CaseStateResponse> {
    await mockDelay(120);
    return {
      title: "아르카디아 스테이션 사건",
      briefing:
        "태양풍 격리 중 사령관 다니엘 로스가 사망했다. 외부 통신은 두절됐고 정거장에 남은 인원은 여섯 명이다.",
      suspectIds: SUSPECTS.map((suspect) => suspect.id),
      evidence: mockDiscoveredEvidence.get(sessionId) ?? [],
    };
  },
  async inspectObject(sessionId: string, objectId: string): Promise<InspectObjectResponse> {
    await mockDelay(180);
    if (shouldFail("inspect")) {
      throw new ArcadiaApiError("증거 기록 장치가 응답하지 않습니다.", "MOCK_INSPECT_TIMEOUT", true);
    }
    const found = mockEvidenceFor(objectId);
    const discovered = mockDiscoveredEvidence.get(sessionId) ?? [];
    const isNew = Boolean(found) && !discovered.some((item) => item.clueId === found?.clueId);
    if (found && isNew) {
      discovered.push(found);
      mockDiscoveredEvidence.set(sessionId, discovered);
    }
    return {
      objectId,
      discoveredEvidence: found && isNew ? [found] : [],
      version: 2,
    };
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
    const sessionId = interrogationId.replace(/^LOCAL-INT-/, "");
    const evidenceTitle =
      mockDiscoveredEvidence
        .get(sessionId)
        ?.find((item) => item.clueId === input.evidenceId)?.title ??
      (input.evidenceId ? input.evidenceId.replace(MOCK_CLUE_PREFIX, "").replaceAll("_", " ") : null);
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
      // 실제 백엔드는 방금 답변에 이어지는 질문을 돌려준다. mock도 같은 자리를 채워
      // 이어서 질문 목록이 정적 질문에서 서버 제안으로 바뀌는 흐름을 그대로 재현한다.
      recommendedQuestions: MOCK_RECOMMENDED_QUESTIONS,
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
    const known = mockDiscoveredEvidence.get(sessionId) ?? [];
    const knownIds = new Set([...known.map((item) => item.clueId), ...discoveredEvidenceIds]);
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
      .map((id) => known.find((item) => item.clueId === id)?.title)
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

export const arcadiaApi = API_MODE === "http" ? httpApi : mockApi;
