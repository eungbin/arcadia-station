import type { TheoryDraft, TrialResult } from "../store/gameStore";

/**
 * 개발용 판정 어댑터.
 * 실제 백엔드 연결 시 동일한 TrialResult를 반환하는 API 호출로 교체한다.
 * 운영 빌드의 정답 데이터로 사용하지 않는다.
 */
const DEV_MOCK_CASES = {
  MAYA: ["CO_BODY", "MD_MEDICAL_TERMINAL", "CO_DOOR_LOG"],
  JUNHO: ["CO_ENV_PANEL", "EN_LIFE_SUPPORT", "CO_TERMINAL", "CO_DOOR_LOG"],
  SOPHIA: ["MD_MEDICAL_TERMINAL", "CO_BODY", "CO_TERMINAL"],
  KASIM: ["CM_SECURITY_ARCHIVE", "CO_DOOR_LOG", "CO_TERMINAL"],
  YUNA: ["CG_AIRLOCK_LOG", "CM_SECURITY_ARCHIVE", "CO_DOOR_LOG"],
} as const;

export type MockCaseId = keyof typeof DEV_MOCK_CASES;

export function resolveMockTrial(
  theory: TheoryDraft,
  culpritId: MockCaseId = "JUNHO",
): TrialResult {
  if (
    !theory.suspectId ||
    !theory.setup ||
    !theory.trigger ||
    !theory.opportunity ||
    !theory.motive
  ) {
    throw new Error("재판 판정에 필요한 이론 항목이 누락되었습니다.");
  }

  const strongEvidenceIds = new Set<string>(DEV_MOCK_CASES[culpritId]);
  // 이론은 서버 단서 ID를 담는다. mock 단서는 조사 오브젝트에서 파생하므로 접두사를 떼고 맞춘다.
  const submittedEvidence = [
    theory.setup,
    theory.trigger,
    theory.opportunity,
    theory.motive,
  ].map((id) => id.replace(/^MOCK-/, ""));
  const strongEvidenceCount = submittedEvidence.filter((id) =>
    strongEvidenceIds.has(id),
  ).length;
  const correctAccusation = theory.suspectId === culpritId;

  if (correctAccusation && strongEvidenceCount >= 2) {
    return {
      accusedId: theory.suspectId,
      votesFor: 5,
      ending: "CULPRIT_EXPELLED",
      correctAccusation: true,
    };
  }

  if (correctAccusation) {
    return {
      accusedId: theory.suspectId,
      votesFor: 3,
      ending: "CULPRIT_SURVIVED",
      correctAccusation: true,
    };
  }

  if (strongEvidenceCount >= 2) {
    return {
      accusedId: theory.suspectId,
      votesFor: 4,
      ending: "INNOCENT_EXPELLED",
      correctAccusation: false,
    };
  }

  return {
    accusedId: theory.suspectId,
    votesFor: 2,
    ending: "TRIAL_DEADLOCK",
    correctAccusation: false,
  };
}
