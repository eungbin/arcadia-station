import { describe, expect, it } from "vitest";
import { validateTheory } from "./theoryValidation";

const completeTheory = {
  suspectId: "JUNHO",
  setup: "E1",
  trigger: "E2",
  opportunity: "E3",
  motive: "E4",
  exclusions: { SOFIA: "E5", KASHIM: "E5" },
};

describe("validateTheory", () => {
  it("accepts a complete theory made from unique discovered evidence", () => {
    expect(
      validateTheory(completeTheory, ["E1", "E2", "E3", "E4", "E5"], ["JUNHO", "SOFIA", "KASHIM"]),
    ).toEqual({ valid: true, message: "논증 구조가 완성되었습니다." });
  });

  it("rejects evidence reused across claims", () => {
    expect(
      validateTheory(
        { ...completeTheory, opportunity: "E1" },
        ["E1", "E2", "E3", "E4", "E5"],
        ["JUNHO", "SOFIA", "KASHIM"],
      ).valid,
    ).toBe(false);
  });

  it("rejects evidence that was not discovered", () => {
    expect(
      validateTheory(completeTheory, ["E1", "E2", "E3", "E4"], ["JUNHO", "SOFIA", "KASHIM"]).valid,
    ).toBe(false);
  });
});
