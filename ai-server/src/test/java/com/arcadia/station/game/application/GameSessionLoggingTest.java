package com.arcadia.station.game.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.game.domain.SessionState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(properties = "arcadia.ai.offline-mode=true")
@ExtendWith(OutputCaptureExtension.class)
class GameSessionLoggingTest {

    @Autowired
    private GameSessionService sessions;

    @Test
    void logsAsyncSessionLifecycleWithoutExposingSeed(CapturedOutput output) throws Exception {
        GameSession session = sessions.createSession("DO_NOT_LOG_SESSION_SEED");
        String readyEvent = "event=session_case_ready sessionId=" + session.sessionId();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));

        while (Instant.now().isBefore(deadline)
                && !output.getOut().contains(readyEvent)) {
            Thread.sleep(25);
        }

        assertThat(session.state()).isEqualTo(SessionState.READY);
        assertThat(output.getOut())
                .contains("event=session_case_accepted sessionId=" + session.sessionId())
                .contains("event=session_case_generation_started sessionId=" + session.sessionId())
                .contains(readyEvent)
                .contains("generationSource=FALLBACK")
                .doesNotContain("DO_NOT_LOG_SESSION_SEED");
    }
}
