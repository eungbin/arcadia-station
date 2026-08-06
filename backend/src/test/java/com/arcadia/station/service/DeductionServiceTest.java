package com.arcadia.station.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcadia.station.domain.EvidenceInventory;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.SessionState;
import com.arcadia.station.dto.response.DeductionResult;
import com.arcadia.station.dto.response.FinalCaseReveal;
import com.arcadia.station.exception.BusinessException;
import com.arcadia.station.exception.ErrorCode;
import com.arcadia.station.repository.EvidenceInventoryRepository;
import com.arcadia.station.repository.GameSessionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StreamUtils;

@SpringBootTest
class DeductionServiceTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private EvidenceInventoryRepository evidenceInventoryRepository;

    @Autowired
    private DeductionService deductionService;

    private static final Set<String> ALL_CORRECT_CLUES =
            Set.of("CLUE-SETUP-LOG", "CLUE-TRIGGER-LOG", "CLUE-ACCESS-HISTORY", "CLUE-MOTIVE-MESSAGE");

    @Test
    void 범인과_네_역할_증거가_전부_정확하면_CORRECT를_반환하고_세션이_COMPLETED된다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        DeductionResult result = deductionService.submit(sessionId, "SOPHIA", correctEvidenceMap(), Map.of());

        assertThat(result.verdict()).isEqualTo("CORRECT");
        assertThat(result.culpritCorrect()).isTrue();
        assertThat(gameSessionRepository.findById(sessionId).orElseThrow().getState())
                .isEqualTo(SessionState.COMPLETED);
    }

    @Test
    void 범인은_맞지만_역할_증거가_틀리면_PARTIAL이고_오답횟수가_증가한다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);
        Map<String, String> evidence = new LinkedHashMap<>(correctEvidenceMap());
        // OPPORTUNITY에 엉뚱한(TRIGGER용) 단서를 제출
        evidence.put("OPPORTUNITY", "CLUE-TRIGGER-LOG");

        DeductionResult result = deductionService.submit(sessionId, "SOPHIA", evidence, Map.of());

        assertThat(result.verdict()).isEqualTo("PARTIAL");
        assertThat(result.roleResults().get("OPPORTUNITY")).isEqualTo("INCORRECT");
        assertThat(result.remainingAttempts()).isEqualTo(2);
        assertThat(evidenceInventoryRepository.findById(sessionId).orElseThrow().getWrongDeductionAttempts())
                .isEqualTo(1);
        assertThat(result.missingLogic())
                .extracting("code", "role")
                .contains(org.assertj.core.groups.Tuple.tuple("WEAK_ROLE_EVIDENCE", "OPPORTUNITY"));
    }

    @Test
    void 범인이_틀리면_역할과_무관하게_INCORRECT다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        DeductionResult result = deductionService.submit(sessionId, "MARCUS", correctEvidenceMap(), Map.of());

        assertThat(result.verdict()).isEqualTo("INCORRECT");
        assertThat(result.culpritCorrect()).isFalse();
        assertThat(result.missingLogic()).extracting("code").containsExactly("WRONG_CULPRIT");
    }

    @Test
    void 배제_근거가_정답이면_exclusionResults가_CORRECT다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        DeductionResult result = deductionService.submit(
                sessionId, "SOPHIA", correctEvidenceMap(), Map.of("MARCUS", "CLUE-ACCESS-HISTORY"));

        assertThat(result.exclusionResults()).containsEntry("MARCUS", "CORRECT");
        assertThat(result.missingLogic()).extracting("code").doesNotContain("WEAK_EXCLUSION");
    }

    @Test
    void 배제_근거가_부족하면_INSUFFICIENT이고_missingLogic에_WEAK_EXCLUSION이_담긴다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        DeductionResult result = deductionService.submit(
                sessionId, "SOPHIA", correctEvidenceMap(), Map.of("MARCUS", "CLUE-SETUP-LOG"));

        assertThat(result.exclusionResults()).containsEntry("MARCUS", "INSUFFICIENT");
        assertThat(result.missingLogic())
                .extracting("code", "characterId")
                .contains(org.assertj.core.groups.Tuple.tuple("WEAK_EXCLUSION", "MARCUS"));
    }

    @Test
    void 범인_본인을_배제_대상으로_보내면_INVALID_REQUEST다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        assertThatThrownBy(() -> deductionService.submit(
                        sessionId, "SOPHIA", correctEvidenceMap(), Map.of("SOPHIA", "CLUE-ACCESS-HISTORY")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 미발견_단서로_배제를_시도하면_INVALID_REQUEST다() throws IOException {
        // 역할 증거는 전부 발견했지만, 배제에 쓰려는 CLUE-FULL-PICTURE(CONNECT형)는 아직 못 찾은 상태.
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        assertThatThrownBy(() -> deductionService.submit(
                        sessionId, "SOPHIA", correctEvidenceMap(), Map.of("MARCUS", "CLUE-FULL-PICTURE")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void exclusionsByCharacter가_null이어도_하위호환으로_기존과_동일하게_동작한다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        DeductionResult result = deductionService.submit(sessionId, "SOPHIA", correctEvidenceMap(), null);

        assertThat(result.verdict()).isEqualTo("CORRECT");
        assertThat(result.exclusionResults()).isEmpty();
    }

    @Test
    void 아직_발견하지_않은_단서를_제출하면_INVALID_REQUEST다() throws IOException {
        String sessionId = seedSession(Set.of("CLUE-TRIGGER-LOG", "CLUE-ACCESS-HISTORY", "CLUE-MOTIVE-MESSAGE"));

        assertThatThrownBy(() -> deductionService.submit(sessionId, "SOPHIA", correctEvidenceMap(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 오답을_최대횟수만큼_제출하면_이후_제출이_차단된다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        for (int i = 0; i < 3; i++) {
            deductionService.submit(sessionId, "MARCUS", correctEvidenceMap(), Map.of());
        }

        // 3번째 오답 즉시 세션이 INCORRECT로 전이되므로(요청 C), 4번째 시도는 오답횟수 초과가 아니라
        // "이미 끝난 세션" 오류로 막힌다.
        assertThatThrownBy(() -> deductionService.submit(sessionId, "MARCUS", correctEvidenceMap(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SESSION_STATE);
    }

    @Test
    void 오답을_최대횟수만큼_제출하면_세션이_INCORRECT로_전이되고_결과가_공개된다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        for (int i = 0; i < 3; i++) {
            deductionService.submit(sessionId, "MARCUS", correctEvidenceMap(), Map.of());
        }

        assertThat(gameSessionRepository.findById(sessionId).orElseThrow().getState())
                .isEqualTo(SessionState.INCORRECT);
        FinalCaseReveal reveal = deductionService.getFinalReveal(sessionId);
        assertThat(reveal.culpritId()).isEqualTo("SOPHIA");
    }

    @Test
    void culpritId가_없으면_500이_아니라_INVALID_REQUEST다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        assertThatThrownBy(() -> deductionService.submit(sessionId, null, correctEvidenceMap(), Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void evidenceByRole이_없으면_500이_아니라_INVALID_REQUEST다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        assertThatThrownBy(() -> deductionService.submit(sessionId, "SOPHIA", null, Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void COMPLETED_이전에는_최종_재구성을_조회할_수_없다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);

        assertThatThrownBy(() -> deductionService.getFinalReveal(sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SESSION_STATE);
    }

    @Test
    void COMPLETED_이후에는_전체_진실이_공개된다() throws IOException {
        String sessionId = seedSession(ALL_CORRECT_CLUES);
        deductionService.submit(sessionId, "SOPHIA", correctEvidenceMap(), Map.of());

        FinalCaseReveal reveal = deductionService.getFinalReveal(sessionId);

        assertThat(reveal.culpritId()).isEqualTo("SOPHIA");
        assertThat(reveal.truthSummary()).isNotBlank();
        assertThat(reveal.solution().culpritId()).isEqualTo("SOPHIA");
    }

    private Map<String, String> correctEvidenceMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("SETUP", "CLUE-SETUP-LOG");
        map.put("TRIGGER", "CLUE-TRIGGER-LOG");
        map.put("OPPORTUNITY", "CLUE-ACCESS-HISTORY");
        map.put("MOTIVE", "CLUE-MOTIVE-MESSAGE");
        return map;
    }

    private String seedSession(Set<String> discoveredClueIds) throws IOException {
        String sessionId = "game_test_deduction_" + UUID.randomUUID();
        GameSession session = new GameSession(sessionId, "req_test_deduction", Instant.now());
        session.setState(SessionState.DEDUCTION);
        session.setCaseBlueprintJson(readFixture());
        gameSessionRepository.save(session);

        EvidenceInventory inventory = new EvidenceInventory(sessionId);
        inventory.getDiscoveredClueIds().addAll(discoveredClueIds);
        evidenceInventoryRepository.save(inventory);
        return sessionId;
    }

    private String readFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/sample-case-blueprint.json")) {
            return new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
        }
    }
}
