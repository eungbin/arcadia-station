import { describe, expect, it } from "vitest";
import { resolveMockTrial } from "./mockVerdict";
import type { TheoryDraft } from "../store/gameStore";

function theory(overrides: Partial<TheoryDraft> = {}): TheoryDraft {
  return {
    suspectId: "JUNHO",
    setup: "CO_ENV_PANEL",
    trigger: "EN_LIFE_SUPPORT",
    opportunity: "CO_DOOR_LOG",
    motive: "CO_TERMINAL",
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
        setup: "CO_BODY",
        trigger: "CG_CARGO_MANIFEST",
        opportunity: "QT_ACCESS_BUFFER",
        motive: "CMN_FOOD_STATION",
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
    expect(() => resolveMockTrial(theory({ opportunity: null }))).toThrow(
      "재판 판정에 필요한 이론 항목이 누락되었습니다.",
    );
  });

  it.each([
    ["MAYA", ["CO_BODY", "MD_MEDICAL_TERMINAL", "CO_DOOR_LOG", "CO_SCANNER"]],
    ["JUNHO", ["CO_ENV_PANEL", "CO_TERMINAL", "EN_LIFE_SUPPORT", "CO_DOOR_LOG"]],
    ["SOPHIA", ["MD_MEDICAL_TERMINAL", "CO_BODY", "CO_TERMINAL", "CO_SCANNER"]],
    ["KASIM", ["CM_SECURITY_ARCHIVE", "CO_DOOR_LOG", "CO_TERMINAL", "CO_SCANNER"]],
    ["YUNA", ["CG_AIRLOCK_LOG", "CM_SECURITY_ARCHIVE", "CO_DOOR_LOG", "CO_SCANNER"]],
  ] as const)("can reproduce the verified fallback for %s", (culpritId, evidence) => {
    const result = resolveMockTrial(
      theory({
        suspectId: culpritId,
        setup: evidence[0],
        trigger: evidence[1],
        opportunity: evidence[2],
        motive: evidence[3],
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
