import type { TheoryDraft } from "../store/gameStore";

export type TheoryValidation = {
  valid: boolean;
  message: string;
};

export function validateTheory(
  theory: TheoryDraft,
  discoveredIds: string[],
  suspectIds: string[],
): TheoryValidation {
  if (!theory.suspectId) return { valid: false, message: "용의자를 지목해야 합니다." };
  if (![theory.method, theory.motive, theory.trace].every(Boolean)) {
    return { valid: false, message: "수단·동기·현장 흔적을 모두 연결해야 합니다." };
  }

  const otherSuspects = suspectIds.filter((id) => id !== theory.suspectId);
  const exclusionIds = otherSuspects.map((id) => theory.exclusions[id]).filter(Boolean);
  if (exclusionIds.length !== otherSuspects.length) {
    return { valid: false, message: "나머지 용의자를 배제할 근거가 필요합니다." };
  }

  const selectedIds = [
    theory.method,
    theory.motive,
    theory.trace,
    ...exclusionIds,
  ] as string[];
  if (selectedIds.some((id) => !discoveredIds.includes(id))) {
    return { valid: false, message: "현장에서 확보한 증거만 제출할 수 있습니다." };
  }
  if (new Set(selectedIds).size !== selectedIds.length) {
    return { valid: false, message: "같은 증거를 여러 주장에 중복 사용할 수 없습니다." };
  }

  return { valid: true, message: "논증 구조가 완성되었습니다." };
}
