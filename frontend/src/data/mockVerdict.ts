import type { MissingLogicItem, VerdictJudgement } from "../api/contracts";
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

/** 백엔드가 오답을 허용하는 기본 횟수(`arcadia.game.deduction.max-wrong-submissions`). */
export const MAX_WRONG_SUBMISSIONS = 3;

/** 백엔드 `DeductionService.ROLE_LABELS`와 같은 문구를 쓴다. */
const ROLE_LABELS: Record<string, string> = {
  SETUP: "수법 설치",
  TRIGGER: "실행 트리거",
  OPPORTUNITY: "기회와 권한",
  MOTIVE: "동기",
};

const THEORY_ROLE_FIELDS = [
  ["SETUP", "setup"],
  ["TRIGGER", "trigger"],
  ["OPPORTUNITY", "opportunity"],
  ["MOTIVE", "motive"],
] as const;

/**
 * mock 판정 상세.
 *
 * 실제 백엔드는 역할별 정오와 부족한 논리를 함께 돌려준다. mock도 같은 모양을 만들어야
 * "틀렸다 → 왜 → 고친다" 흐름을 서버 없이 확인할 수 있다. 정답 단서 ID나 정답 인물은
 * 백엔드와 마찬가지로 담지 않는다.
 */
export function resolveMockJudgement(
  theory: TheoryDraft,
  culpritId: MockCaseId,
  wrongAttemptsBefore: number,
): VerdictJudgement {
  const strongEvidenceIds = new Set<string>(DEV_MOCK_CASES[culpritId]);
  const strip = (id: string) => id.replace(/^MOCK-/, "");

  const roleResults: VerdictJudgement["roleResults"] = {};
  for (const [role, field] of THEORY_ROLE_FIELDS) {
    const clueId = theory[field];
    roleResults[role] = clueId && strongEvidenceIds.has(strip(clueId)) ? "CORRECT" : "INCORRECT";
  }

  // 범인을 가리키는 기록으로 다른 사람을 배제할 수는 없다. mock에는 별도 배제 정답표가 없어
  // 이 규칙으로 대신한다.
  const exclusionResults: VerdictJudgement["exclusionResults"] = {};
  for (const [characterId, clueId] of Object.entries(theory.exclusions)) {
    if (characterId === theory.suspectId || !clueId) continue;
    exclusionResults[characterId] = strongEvidenceIds.has(strip(clueId))
      ? "INSUFFICIENT"
      : "CORRECT";
  }

  const culpritCorrect = theory.suspectId === culpritId;
  const allRolesCorrect = Object.values(roleResults).every((mark) => mark === "CORRECT");
  const verdict = !culpritCorrect ? "INCORRECT" : allRolesCorrect ? "CORRECT" : "PARTIAL";
  const wrongAttempts = wrongAttemptsBefore + (verdict === "CORRECT" ? 0 : 1);

  const missingLogic: MissingLogicItem[] = [];
  if (!culpritCorrect) {
    missingLogic.push({
      code: "WRONG_CULPRIT",
      role: null,
      characterId: null,
      message: "제시한 용의자는 이 사건의 범인이 아닙니다.",
    });
  } else {
    for (const [role, mark] of Object.entries(roleResults)) {
      if (mark !== "INCORRECT") continue;
      missingLogic.push({
        code: "WEAK_ROLE_EVIDENCE",
        role,
        characterId: null,
        message: `${ROLE_LABELS[role] ?? role} 증거가 부족합니다.`,
      });
    }
  }
  for (const [characterId, mark] of Object.entries(exclusionResults)) {
    if (mark !== "INSUFFICIENT") continue;
    missingLogic.push({
      code: "WEAK_EXCLUSION",
      role: null,
      characterId,
      message: `${characterId}를 배제할 근거가 부족합니다.`,
    });
  }

  return {
    verdict,
    culpritCorrect,
    roleResults,
    exclusionResults,
    remainingAttempts: Math.max(0, MAX_WRONG_SUBMISSIONS - wrongAttempts),
    feedback: buildMockFeedback(verdict, culpritCorrect, roleResults),
    missingLogic,
  };
}

function buildMockFeedback(
  verdict: VerdictJudgement["verdict"],
  culpritCorrect: boolean,
  roleResults: VerdictJudgement["roleResults"],
): string {
  if (verdict === "CORRECT") return "정확한 추리입니다. 사건의 전모가 드러났습니다.";
  if (!culpritCorrect) return "제시한 용의자는 이 사건의 범인이 아닙니다. 다시 조사해보세요.";
  const wrongLabels = Object.entries(roleResults)
    .filter(([, mark]) => mark === "INCORRECT")
    .map(([role]) => ROLE_LABELS[role] ?? role)
    .join(", ");
  return `범인은 맞지만 ${wrongLabels} 증거를 다시 확인해야 합니다.`;
}
