import { describe, expect, it } from "vitest";
import { REQUIRED_SCENE_IDS } from "../data/investigation";
import { getRequiredProgress } from "./gameStore";

describe("getRequiredProgress", () => {
  it("reports incomplete progress when evidence is missing", () => {
    expect(getRequiredProgress(REQUIRED_SCENE_IDS.slice(0, 2))).toEqual({
      found: 2,
      total: 4,
      complete: false,
    });
  });

  it("reports completion only when all required scene records exist", () => {
    expect(getRequiredProgress(REQUIRED_SCENE_IDS)).toEqual({
      found: 4,
      total: 4,
      complete: true,
    });
  });

  it("ignores non-required evidence", () => {
    expect(getRequiredProgress(["CO_SCANNER", "HB_MAINTENANCE"])).toEqual({
      found: 0,
      total: 4,
      complete: false,
    });
  });
});
