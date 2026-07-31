import type { TheoryDraft } from "../store/gameStore";

export type TheoryValidation = {
  valid: boolean;
  message: string;
};

/**
 * 이론 초안 검증. `discoveredIds`와 이론의 증거 필드는 모두 서버 단서 ID다.
 *
 * 핵심 3축은 서로 다른 기록으로 세워야 하지만, 배제 근거는 같은 기록을 여러 명에게 다시 쓸 수
 * 있다. 서버가 생성하는 사건의 단서는 보통 5개 안팎이라 3축과 배제 4건 모두에 고유 기록을
 * 요구하면 재판을 열 수 없기 때문이다. 출입 기록 하나가 여러 명을 동시에 배제하는 것도
 * 추리로서 자연스럽다.
 */
export function validateTheory(
  theory: TheoryDraft,
  discoveredIds: string[],
  suspectIds: string[],
): TheoryValidation {
  if (!theory.suspectId) return { valid: false, message: "용의자를 지목해야 합니다." };
  if (![theory.setup, theory.trigger, theory.opportunity, theory.motive].every(Boolean)) {
    return { valid: false, message: "준비·실행·기회·동기를 모두 연결해야 합니다." };
  }

  const otherSuspects = suspectIds.filter((id) => id !== theory.suspectId);
  const exclusionIds = otherSuspects.map((id) => theory.exclusions[id]).filter(Boolean);
  if (exclusionIds.length !== otherSuspects.length) {
    return { valid: false, message: "나머지 용의자를 배제할 근거가 필요합니다." };
  }

  const coreIds = [
    theory.setup,
    theory.trigger,
    theory.opportunity,
    theory.motive,
  ] as string[];
  if ([...coreIds, ...exclusionIds].some((id) => !discoveredIds.includes(id))) {
    return { valid: false, message: "현장에서 확보한 기록만 제출할 수 있습니다." };
  }
  if (new Set(coreIds).size !== coreIds.length) {
    return { valid: false, message: "네 축은 서로 다른 기록이어야 합니다." };
  }

  return { valid: true, message: "논증 구조가 완성되었습니다." };
}
