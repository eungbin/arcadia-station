package com.arcadia.station.ai.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class QuotaAwareAiGatewayTest {

    @Test
    void blocksAllProviderCallsUntilCooldownExpires() {
        AtomicLong now = new AtomicLong(1_000);
        TestGateway delegate = new TestGateway();
        QuotaAwareAiGateway gateway = new QuotaAwareAiGateway(
                delegate,
                "GEMINI",
                Duration.ofMinutes(10),
                new AiUsageRecorder(new SimpleMeterRegistry()),
                now::get
        );

        assertThatThrownBy(() -> gateway.createEmbedding("first"))
                .isInstanceOf(AiQuotaExceededException.class);
        assertThat(delegate.calls).isEqualTo(1);

        assertThatThrownBy(() -> gateway.createEmbedding("blocked"))
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessageContaining("cooldown");
        assertThat(delegate.calls).isEqualTo(1);

        now.addAndGet(Duration.ofMinutes(10).toMillis() + 1);
        delegate.quotaExceeded = false;

        assertThat(gateway.createEmbedding("after-cooldown"))
                .containsExactly(0.25f);
        assertThat(delegate.calls).isEqualTo(2);
    }

    private static final class TestGateway implements OpenAiGateway {

        private int calls;
        private boolean quotaExceeded = true;

        @Override
        public <T> T generateStructured(
                AiPurpose purpose,
                String promptVersion,
                Object promptContext,
                JsonSchema schema,
                Class<T> responseType
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] createEmbedding(String input) {
            calls++;
            if (quotaExceeded) {
                throw new AiQuotaExceededException("quota exhausted");
            }
            return new float[] {0.25f};
        }
    }
}
