package com.arcadia.station.game.application;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
import com.arcadia.station.ai.template.TemplateRepository;
import com.arcadia.station.game.domain.GameSession;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DeductionService {

    private final GameSessionService sessions;
    private final TemplateRepository templates;

    public DeductionService(
            GameSessionService sessions,
            TemplateRepository templates
    ) {
        this.sessions = sessions;
        this.templates = templates;
    }

    public DeductionResponse submit(String sessionId, DeductionRequest request) {
        GameSession session = sessions.requireSession(sessionId);
        CaseBlueprint blueprint = sessions.requireFrozenCase(sessionId).blueprint();
        int maximumAttempts = templates.rules().finalReportRules().maxWrongSubmissions();
        if (session.remainingAttempts(maximumAttempts) == 0) {
            throw new IllegalStateException("No deduction attempts remain");
        }
        session.startDeduction();

        boolean culpritCorrect = blueprint.culpritId().equals(request.culpritId());
        Map<EvidenceRole, RoleResult> roleResults = new EnumMap<>(EvidenceRole.class);
        for (EvidenceRole role : templates.rules().finalReportRules().requiredRoles()) {
            String submittedClueId = request.evidenceByRole().get(role);
            if (submittedClueId == null || submittedClueId.isBlank()) {
                roleResults.put(role, RoleResult.MISSING);
                continue;
            }
            if (!session.evidenceInventory().contains(submittedClueId)) {
                roleResults.put(role, RoleResult.NOT_DISCOVERED);
                continue;
            }
            Set<String> accepted = new LinkedHashSet<>(
                    blueprint.solution().requiredEvidenceByRole().getOrDefault(role, List.of())
            );
            accepted.addAll(
                    blueprint.solution().acceptedAlternativesByRole()
                            .getOrDefault(role, List.of())
            );
            roleResults.put(
                    role,
                    accepted.contains(submittedClueId)
                            ? RoleResult.CORRECT
                            : RoleResult.INCORRECT
            );
        }

        boolean allRolesCorrect = roleResults.values().stream()
                .allMatch(result -> result == RoleResult.CORRECT);
        boolean solved = culpritCorrect && allRolesCorrect;
        int remainingAttempts;
        Verdict verdict;
        if (solved) {
            session.complete();
            remainingAttempts = session.remainingAttempts(
                    maximumAttempts
            );
            verdict = Verdict.CORRECT;
        } else {
            remainingAttempts = session.registerWrongSubmission(
                    maximumAttempts
            );
            verdict = culpritCorrect || roleResults.containsValue(RoleResult.CORRECT)
                    ? Verdict.PARTIAL
                    : Verdict.INCORRECT;
        }
        return new DeductionResponse(
                verdict,
                culpritCorrect,
                Map.copyOf(roleResults),
                remainingAttempts,
                feedback(verdict, culpritCorrect, roleResults)
        );
    }

    private String feedback(
            Verdict verdict,
            boolean culpritCorrect,
            Map<EvidenceRole, RoleResult> roles
    ) {
        if (verdict == Verdict.CORRECT) {
            return "사건의 준비, 촉발, 기회와 동기를 모두 입증했습니다.";
        }
        if (culpritCorrect) {
            List<String> incorrectRoles = roles.entrySet().stream()
                    .filter(entry -> entry.getValue() != RoleResult.CORRECT)
                    .map(entry -> entry.getKey().name())
                    .toList();
            return "범인은 맞지만 다음 추리 축의 증거를 다시 확인해야 합니다: "
                    + String.join(", ", incorrectRoles);
        }
        return "범인과 각 추리 축을 뒷받침하는 발견 증거를 다시 검토해 주세요.";
    }

    public record DeductionRequest(
            String culpritId,
            Map<EvidenceRole, String> evidenceByRole
    ) {}

    public record DeductionResponse(
            Verdict verdict,
            boolean culpritCorrect,
            Map<EvidenceRole, RoleResult> roleResults,
            int remainingAttempts,
            String feedback
    ) {}

    public enum Verdict {
        CORRECT,
        PARTIAL,
        INCORRECT
    }

    public enum RoleResult {
        CORRECT,
        INCORRECT,
        MISSING,
        NOT_DISCOVERED
    }
}
