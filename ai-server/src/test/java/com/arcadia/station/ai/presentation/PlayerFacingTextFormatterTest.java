package com.arcadia.station.ai.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlayerFacingTextFormatterTest {

    private final PlayerFacingTextFormatter formatter = new PlayerFacingTextFormatter();

    @Test
    void hidesKnownInternalIdsAndCommandsFromPlayerText() {
        String formatted = formatter.format(
                "REC_001: SOPHIA가 CENTRAL_HUB에서 RUN_SAFETY_DIAGNOSTIC을 실행했다. "
                        + "(FACT-TRIGGER, CLUE-TRIGGER-LOG)"
        );

        assertThat(formatted)
                .contains("소피아", "중앙 허브", "안전 진단", "해당 기록")
                .doesNotContain(
                        "REC_001",
                        "SOPHIA",
                        "CENTRAL_HUB",
                        "RUN_SAFETY_DIAGNOSTIC",
                        "FACT-TRIGGER",
                        "CLUE-TRIGGER-LOG"
                );
    }

    @Test
    void replacesUnknownSnakeCaseCommandsWithNeutralPlayerWording() {
        String formatted = formatter.format("UNKNOWN_INTERNAL_COMMAND 결과를 확인했다.");

        assertThat(formatted)
                .contains("해당 시스템 작업")
                .doesNotContain("UNKNOWN_INTERNAL_COMMAND");
    }
}
