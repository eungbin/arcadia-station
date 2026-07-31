/**
 * 실제 게임 백엔드를 상대로 도는 연동 스모크 테스트.
 *
 * 계약이 어긋났는지 확인할 때만 수동으로 돌린다. 백엔드(8080)가 떠 있어야 하므로
 * 기본 `npm test`에서는 건너뛴다.
 *
 * ```bash
 * cd ../backend && docker compose up -d --build
 * cd ../frontend
 * VITE_LIVE_BACKEND=1 VITE_API_BASE_URL=http://localhost:8080/api npx vitest run src/api/httpApi.live.test.ts
 * ```
 */
import { beforeAll, describe, expect, it, vi } from "vitest";
import { httpApi } from "./httpApi";
import { OBJECT_BINDINGS } from "./backendContract";
import type { TheoryDraft } from "../store/gameStore";

const log = (...args: unknown[]) => console.log(...args);

beforeAll(() => {
  const store = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, String(v)),
    removeItem: (k: string) => void store.delete(k),
    clear: () => store.clear(),
  });
  vi.stubGlobal("window", {
    setTimeout: (fn: () => void, ms: number) => globalThis.setTimeout(fn, ms),
  });
});

describe.skipIf(!import.meta.env.VITE_LIVE_BACKEND)("라이브 백엔드 연동", () => {
  let sessionId = "";

  it("세션을 만들고 사건 생성 완료까지 기다린다", async () => {
    const session = await httpApi.createSession();
    sessionId = session.sessionId;
    log("[1] createSession ->", JSON.stringify(session));
    expect(sessionId).toMatch(/^game_/);
    expect(["READY", "IN_PROGRESS"]).toContain(session.status);
    // 실제 AI 사건 생성은 수십 초가 걸린다.
  }, 200_000);

  it("오프닝 완료로 사건 동결을 확인한다", async () => {
    const session = await httpApi.completeOpening(sessionId);
    log("[2] completeOpening ->", JSON.stringify(session));
    expect(session.status).toBe("IN_PROGRESS");
  }, 30_000);

  it("조사 오브젝트 16종을 모두 조사한다", async () => {
    for (const objectId of Object.keys(OBJECT_BINDINGS)) {
      const result = await httpApi.inspectObject(sessionId, objectId);
      for (const record of result.discoveredEvidence) {
        expect(record.clueId).toBeTruthy();
        expect(record.playerText).toBeTruthy();
      }
    }
    // 배경 RAG 질의가 끝날 시간을 준다.
    await new Promise((resolve) => globalThis.setTimeout(resolve, 3_000));
    const raw = JSON.parse(localStorage.getItem("arcadia-station-backend-ledger-v1") ?? "{}");
    log("[3] 원장 discoveredClueIds ->", JSON.stringify(raw.discoveredClueIds));
    log("[3] 원장 objectClues ->", JSON.stringify(raw.objectClues));
  }, 120_000);

  it("수사 보조 탭 검색으로 기록 단서를 확보한다", async () => {
    // 기록 검색은 이 탭에서만 일어난다. 조사는 장소 탐사만 한다.
    const queries = [
      "02:05 안전 진단 기록을 보여줘",
      "출입 기록과 각 구역 원본 사이의 모순을 찾아줘",
      "피해자의 감사 보고서와 면담 통보 기록을 보여 줘",
      "자원 할당 변경과 감사 대상 기록을 보여 줘",
    ];
    for (const query of queries) {
      const answer = await httpApi.askAssistant(sessionId, query, []);
      log(`[4] "${query}" -> ${answer.observation}`);
      expect(typeof answer.summary).toBe("string");
    }
  }, 300_000);

  it("사건 개요와 확보 단서를 서버에서 받아온다", async () => {
    const state = await httpApi.fetchCaseState(sessionId);
    log("[4b] 사건 제목 ->", state.title);
    log("[4b] 브리핑 ->", state.briefing);
    log("[4b] 용의자 ->", JSON.stringify(state.suspectIds));
    log(
      "[4b] 단서 ->",
      JSON.stringify(state.evidence.map((r) => `${r.clueType}:${r.title}`)),
    );
    expect(state.title).toBeTruthy();
    expect(state.briefing).toBeTruthy();
    expect(state.suspectIds.length).toBeGreaterThan(0);
    for (const record of state.evidence) {
      expect(record.playerText).toBeTruthy();
    }
  }, 30_000);

  it("용의자를 심문한다", async () => {
    const opened = await httpApi.startInterrogation(sessionId, "NPC_SOPHIA");
    log("[5] startInterrogation ->", opened.interrogationId);

    const choice = await httpApi.sendInterrogationMessage(opened.interrogationId, {
      npcId: "NPC_SOPHIA",
      choiceId: "cause",
    });
    log("[5] 선택 질문 ->", JSON.stringify(choice.response));
    expect(choice.response.length).toBeGreaterThan(0);

    const free = await httpApi.sendInterrogationMessage(opened.interrogationId, {
      npcId: "NPC_SOPHIA",
      query: "02:05에 어디에 있었습니까?",
    });
    log("[5] 자유 질문 ->", JSON.stringify(free.response));

    const evidence = await httpApi.sendInterrogationMessage(opened.interrogationId, {
      npcId: "NPC_SOPHIA",
      evidenceId: "MD_MEDICAL_STORAGE",
    });
    log("[5] 증거 제시 ->", JSON.stringify(evidence.response));
  }, 120_000);

  it("일차 종료와 이론 저장을 처리한다", async () => {
    // 백엔드에 대응 엔드포인트가 없어 클라이언트에서만 처리하는 항목이다.
    log("[6] completeDay ->", JSON.stringify(await httpApi.completeDay(sessionId, 1)));
    log("[6] saveTheory ->", JSON.stringify(
      await httpApi.saveTheory(sessionId, {
        theory: {
          suspectId: "SOPHIA",
          setup: null,
          trigger: null,
          opportunity: null,
          motive: null,
          exclusions: {},
        },
        version: 1,
      }),
    ));
  });

  it("최종 추리를 제출하고 재판 결과를 받는다", async () => {
    // 이론은 서버 단서 ID로 세운다. 사건마다 단서가 달라 공개 상태에서 골라야 한다.
    const state = await httpApi.fetchCaseState(sessionId);
    log("[7] 확보한 단서 ->", JSON.stringify(state.evidence.map((record) => record.clueId)));
    expect(state.evidence.length).toBeGreaterThanOrEqual(3);

    // 네 축을 서로 다른 단서로 채운다. 종류가 맞는 단서를 우선 고른다.
    const used = new Set<string>();
    const take = (preferredType: string) => {
      const match =
        state.evidence.find(
          (record) => record.clueType === preferredType && !used.has(record.clueId),
        ) ?? state.evidence.find((record) => !used.has(record.clueId));
      const clueId = match?.clueId ?? state.evidence[0].clueId;
      used.add(clueId);
      return clueId;
    };
    const theory: TheoryDraft = {
      suspectId: "SOPHIA",
      setup: take("PHYSICAL"),
      trigger: take("DIGITAL"),
      opportunity: take("OPPORTUNITY"),
      motive: take("MOTIVE"),
      exclusions: {},
    };
    log("[7] 제출 이론 ->", JSON.stringify(theory));
    const result = await httpApi.submitVerdict(sessionId, theory);
    log("[7] submitVerdict ->", JSON.stringify(result));
    expect(result.accusedId).toBe("SOPHIA");
    expect([
      "CULPRIT_EXPELLED",
      "CULPRIT_SURVIVED",
      "INNOCENT_EXPELLED",
      "TRIAL_DEADLOCK",
    ]).toContain(result.ending);
  }, 60_000);
});
