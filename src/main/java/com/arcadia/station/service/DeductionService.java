package com.arcadia.station.service;

import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.Clue;
import com.arcadia.station.domain.caseblueprint.Solution;
import com.arcadia.station.dto.response.DeductionResult;
import com.arcadia.station.dto.response.FinalCaseReveal;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 9장 최종 추리 판정. LLM을 호출하지 않는 순수 Java 결정 로직.
 */
@Service
@Transactional
public class DeductionService {

    private static final List<String> REQUIRED_ROLES = List.of("SETUP", "TRIGGER", "OPPORTUNITY", "MOTIVE");
    private static final Map<String, String> ROLE_LABELS = Map.of(
            "SETUP", "수법 설치",
            "TRIGGER", "실행 트리거",
            "OPPORTUNITY", "기회와 권한",
            "MOTIVE", "동기");

    private final GameSessionRepository gameSessionRepository;
    private final EvidenceInventoryRepository evidenceInventoryRepository;
    private final ObjectMapper objectMapper;
    private final int maxWrongSubmissions;

    public DeductionService(
            GameSessionRepository gameSessionRepository,
            EvidenceInventoryRepository evidenceInventoryRepository,
            ObjectMapper objectMapper,
            @Value("${arcadia.game.deduction.max-wrong-submissions:3}") int maxWrongSubmissions) {
        this.gameSessionRepository = gameSessionRepository;
        this.evidenceInventoryRepository = evidenceInventoryRepository;
        this.objectMapper = objectMapper;
        this.maxWrongSubmissions = maxWrongSubmissions;
    }

    public DeductionResult submit(String sessionId, String culpritId, Map<String, String> evidenceByRole) {
        if (culpritId == null || culpritId.isBlank() || evidenceByRole == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        GameSession session = findSessionOrThrow(sessionId);
        requireReady(session);

        EvidenceInventory inventory = evidenceInventoryRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (inventory.getWrongDeductionAttempts() >= maxWrongSubmissions) {
            throw new BusinessException(ErrorCode.DEDUCTION_LIMIT_EXCEEDED);
        }
        if (!evidenceByRole.keySet().containsAll(REQUIRED_ROLES) || evidenceByRole.size() != REQUIRED_ROLES.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        CaseBlueprint blueprint = objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
        Solution solution = blueprint.solution();

        Map<String, String> roleResults = new LinkedHashMap<>();
        for (String role : REQUIRED_ROLES) {
            String clueId = evidenceByRole.get(role);
            Clue clue = findClueOrThrow(blueprint, clueId);
            if (!inventory.getDiscoveredClueIds().contains(clueId)) {
                // 미획득 단서로 제출 불가(9.2절)
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            roleResults.put(role, isCorrectForRole(clue, role, solution) ? "CORRECT" : "INCORRECT");
        }

        boolean culpritCorrect = culpritId.equals(solution.culpritId());
        boolean allRolesCorrect = roleResults.values().stream().allMatch("CORRECT"::equals);
        String verdict = !culpritCorrect ? "INCORRECT" : (allRolesCorrect ? "CORRECT" : "PARTIAL");

        if ("CORRECT".equals(verdict)) {
            session.setState(SessionState.COMPLETED);
            gameSessionRepository.save(session);
        } else {
            inventory.setWrongDeductionAttempts(inventory.getWrongDeductionAttempts() + 1);
            evidenceInventoryRepository.save(inventory);
        }

        int remainingAttempts = Math.max(0, maxWrongSubmissions - inventory.getWrongDeductionAttempts());
        String feedback = buildFeedback(verdict, culpritCorrect, roleResults);
        return new DeductionResult(verdict, culpritCorrect, roleResults, remainingAttempts, feedback);
    }

    public FinalCaseReveal getFinalReveal(String sessionId) {
        GameSession session = findSessionOrThrow(sessionId);
        if (session.getState() != SessionState.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
        CaseBlueprint blueprint = objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
        return new FinalCaseReveal(
                sessionId,
                blueprint.culpritId(),
                blueprint.truthSummary(),
                blueprint.method(),
                blueprint.timeline(),
                blueprint.facts(),
                blueprint.alibis(),
                blueprint.solution());
    }

    private boolean isCorrectForRole(Clue clue, String role, Solution solution) {
        boolean taggedForRole = clue.solutionRoles().stream().anyMatch(r -> r.name().equals(role));
        boolean isRequired = solution.requiredEvidenceByRole().getOrDefault(role, List.of()).contains(clue.clueId());
        boolean isAccepted = solution.acceptedAlternativesByRole().getOrDefault(role, List.of()).contains(clue.clueId());
        return taggedForRole && (isRequired || isAccepted);
    }

    // 오답 상태에서 정답 단서 ID나 아직 찾지 못한 사실을 노출하지 않는다(9.3절).
    private String buildFeedback(String verdict, boolean culpritCorrect, Map<String, String> roleResults) {
        if ("CORRECT".equals(verdict)) {
            return "정확한 추리입니다. 사건의 전모가 드러났습니다.";
        }
        if (!culpritCorrect) {
            return "제시한 용의자는 이 사건의 범인이 아닙니다. 다시 조사해보세요.";
        }
        String wrongRoleLabels = roleResults.entrySet().stream()
                .filter(entry -> "INCORRECT".equals(entry.getValue()))
                .map(entry -> ROLE_LABELS.get(entry.getKey()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "범인은 맞지만 " + wrongRoleLabels + " 증거를 다시 확인해야 합니다.";
    }

    private Clue findClueOrThrow(CaseBlueprint blueprint, String clueId) {
        return blueprint.clues().stream()
                .filter(clue -> clue.clueId().equals(clueId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
    }

    private void requireReady(GameSession session) {
        if (session.getCaseBlueprintJson() == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_READY);
        }
        if (session.getState() == SessionState.COMPLETED || session.getState() == SessionState.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATE);
        }
    }

    private GameSession findSessionOrThrow(String sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
