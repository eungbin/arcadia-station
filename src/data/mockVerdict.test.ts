import { describe, expect, it } from "vitest";
import { resolveMockTrial } from "./mockVerdict";
import type { TheoryDraft } from "../store/gameStore";

function theory(overrides: Partial<TheoryDraft> = {}): TheoryDraft {
  return {
    suspectId: "JUNHO",
    method: "CO_ENV_PANEL",
    motive: "CO_TERMINAL",
    trace: "EN_LIFE_SUPPORT",
    exclusions: {
      MAYA: "CO_XO_PASSAGE",
      SOPHIA: "MD_MEDICAL_TERMINAL",
      KASIM: "CM_SECURITY_ARCHIVE",
      YUNA: "CG_AIRLOCK_LOG",
    },
    ...overrides,
  };
}

describe("resolveMockTrial", () => {
  it("convicts the culprit when the accusation and proof are valid", () => {
    expect(resolveMockTrial(theory())).toEqual({
      accusedId: "JUNHO",
      votesFor: 5,
      ending: "CULPRIT_EXPELLED",
      correctAccusation: true,
    });
  });

  it("keeps the culprit alive when the evidence is weak", () => {
    const result = resolveMockTrial(
      theory({
        method: "CO_BODY",
        motive: "CG_CARGO_MANIFEST",
        trace: "QT_ACCESS_BUFFER",
      }),
    );

    expect(result.ending).toBe("CULPRIT_SURVIVED");
    expect(result.votesFor).toBeLessThan(4);
  });

  it("allows a persuasive but wrong theory to produce a false conviction", () => {
    const result = resolveMockTrial(theory({ suspectId: "YUNA" }));

    expect(result).toMatchObject({
      accusedId: "YUNA",
      votesFor: 4,
      ending: "INNOCENT_EXPELLED",
      correctAccusation: false,
    });
  });

  it("rejects an incomplete theory", () => {
    expect(() => resolveMockTrial(theory({ trace: null }))).toThrow(
      "재판 판정에 필요한 이론 항목이 누락되었습니다.",
    );
  });

  it.each([
    ["MAYA", ["CO_BODY", "MD_MEDICAL_TERMINAL", "CO_DOOR_LOG"]],
    ["JUNHO", ["CO_ENV_PANEL", "CO_TERMINAL", "EN_LIFE_SUPPORT"]],
    ["SOPHIA", ["MD_MEDICAL_TERMINAL", "CO_BODY", "CO_TERMINAL"]],
    ["KASIM", ["CM_SECURITY_ARCHIVE", "CO_DOOR_LOG", "CO_TERMINAL"]],
    ["YUNA", ["CG_AIRLOCK_LOG", "CM_SECURITY_ARCHIVE", "CO_DOOR_LOG"]],
  ] as const)("can reproduce the verified fallback for %s", (culpritId, evidence) => {
    const result = resolveMockTrial(
      theory({
        suspectId: culpritId,
        method: evidence[0],
        motive: evidence[1],
        trace: evidence[2],
      }),
      culpritId,
    );

    expect(result).toMatchObject({
      accusedId: culpritId,
      ending: "CULPRIT_EXPELLED",
      correctAccusation: true,
    });
  });
});
