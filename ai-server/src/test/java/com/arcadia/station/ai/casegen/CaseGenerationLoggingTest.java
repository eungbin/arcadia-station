package com.arcadia.station.ai.casegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(properties = {
        "arcadia.ai.enabled=true",
        "arcadia.ai.offline-mode=true",
        "arcadia.ai.openai.api-key=DO_NOT_LOG_API_KEY"
})
@ExtendWith(OutputCaptureExtension.class)
class CaseGenerationLoggingTest {

    private static final Pattern CLUE_SET_SHA = Pattern.compile(
            "clueSetSha256=([0-9a-f]{64})"
    );

    @Autowired
    private CaseGenerationService service;

    @Autowired
    private CaseGenerationDiagnostics diagnostics;

    @Test
    void logsOfflineFallbackReasonAndSafeClueSummary(CapturedOutput output) {
        service.createCase("logging-session-one", "DO_NOT_LOG_SEED_ONE");
        service.createCase("logging-session-two", "DO_NOT_LOG_SEED_TWO");

        String logs = output.getOut();
        assertThat(logs)
                .contains("event=case_generation_started sessionId=logging-session-one")
                .contains("apiKeyConfigured=true externalAiEnabled=false")
                .contains("event=case_generation_completed sessionId=logging-session-one")
                .contains("generationSource=FALLBACK fallbackReason=OFFLINE_MODE")
                .contains("aiPathAttempted=false")
                .contains("clueCount=14 exploreClueCount=10 objectClueCount=10")
                .contains("event=case_generation_clues sessionId=logging-session-one")
                .contains("\"clueId\":\"CLUE-SETUP-PANEL\"")
                .contains("\"title\":")
                .doesNotContain("DO_NOT_LOG_API_KEY")
                .doesNotContain("DO_NOT_LOG_SEED_ONE")
                .doesNotContain("DO_NOT_LOG_SEED_TWO");

        List<String> fingerprints = logs.lines()
                .filter(line -> line.contains("event=case_generation_completed"))
                .map(CLUE_SET_SHA::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();
        assertThat(fingerprints)
                .hasSize(2)
                .containsOnly(fingerprints.getFirst());
    }

    @Test
    void diagnosticsFailureNeverBreaksCaseGeneration(CapturedOutput output) {
        assertThatCode(() -> diagnostics.generationCompleted(
                null,
                CaseGenerationFallbackReason.NONE,
                false,
                Instant.now()
        )).doesNotThrowAnyException();

        assertThat(output.getOut())
                .contains("event=case_generation_diagnostics_failed")
                .contains("diagnosticEvent=case_generation_completed")
                .contains("exceptionType=NullPointerException");
    }
}
