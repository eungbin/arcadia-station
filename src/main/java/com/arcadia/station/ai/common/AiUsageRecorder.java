package com.arcadia.station.ai.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class AiUsageRecorder {

    private final MeterRegistry registry;

    public AiUsageRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordCall(
            AiPurpose purpose,
            Duration duration,
            boolean success,
            long inputTokens,
            long outputTokens
    ) {
        String outcome = success ? "success" : "failure";
        Timer.builder("arcadia.ai.call.duration")
                .tag("purpose", purpose.name())
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
        DistributionSummary.builder("arcadia.ai.tokens")
                .baseUnit("tokens")
                .tag("purpose", purpose.name())
                .tag("direction", "input")
                .register(registry)
                .record(Math.max(0, inputTokens));
        DistributionSummary.builder("arcadia.ai.tokens")
                .baseUnit("tokens")
                .tag("purpose", purpose.name())
                .tag("direction", "output")
                .register(registry)
                .record(Math.max(0, outputTokens));
    }

    public void recordValidationIssue(String code) {
        Counter.builder("arcadia.ai.case.validation.issues")
                .tag("code", code)
                .register(registry)
                .increment();
    }

    public void recordGenerationSource(String source) {
        Counter.builder("arcadia.ai.case.generation")
                .tag("source", source)
                .register(registry)
                .increment();
    }

    public void recordQuotaCircuitOpened(String provider) {
        Counter.builder("arcadia.ai.quota.circuit.opened")
                .tag("provider", provider)
                .register(registry)
                .increment();
    }
}
