package com.arcadia.station.ai.common;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class QuotaAwareAiGateway implements OpenAiGateway {

    private final OpenAiGateway delegate;
    private final String provider;
    private final long cooldownMillis;
    private final AiUsageRecorder usageRecorder;
    private final LongSupplier currentTimeMillis;
    private final AtomicLong blockedUntilMillis = new AtomicLong();

    public QuotaAwareAiGateway(
            OpenAiGateway delegate,
            String provider,
            Duration cooldown,
            AiUsageRecorder usageRecorder
    ) {
        this(
                delegate,
                provider,
                cooldown,
                usageRecorder,
                System::currentTimeMillis
        );
    }

    QuotaAwareAiGateway(
            OpenAiGateway delegate,
            String provider,
            Duration cooldown,
            AiUsageRecorder usageRecorder,
            LongSupplier currentTimeMillis
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.provider = Objects.requireNonNull(provider);
        this.cooldownMillis = Math.max(0, Objects.requireNonNull(cooldown).toMillis());
        this.usageRecorder = Objects.requireNonNull(usageRecorder);
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis);
    }

    @Override
    public <T> T generateStructured(
            AiPurpose purpose,
            String promptVersion,
            Object promptContext,
            JsonSchema schema,
            Class<T> responseType
    ) {
        rejectWhileCoolingDown();
        try {
            return delegate.generateStructured(
                    purpose,
                    promptVersion,
                    promptContext,
                    schema,
                    responseType
            );
        } catch (AiQuotaExceededException exception) {
            openCircuit();
            throw exception;
        }
    }

    @Override
    public float[] createEmbedding(String input) {
        rejectWhileCoolingDown();
        try {
            return delegate.createEmbedding(input);
        } catch (AiQuotaExceededException exception) {
            openCircuit();
            throw exception;
        }
    }

    private void rejectWhileCoolingDown() {
        long remainingMillis = blockedUntilMillis.get() - currentTimeMillis.getAsLong();
        if (remainingMillis > 0) {
            throw new AiQuotaExceededException(
                    provider + " quota cooldown is active for "
                            + Duration.ofMillis(remainingMillis).toSeconds()
                            + " more seconds"
            );
        }
    }

    private void openCircuit() {
        long blockedUntil = currentTimeMillis.getAsLong() + cooldownMillis;
        blockedUntilMillis.accumulateAndGet(blockedUntil, Math::max);
        usageRecorder.recordQuotaCircuitOpened(provider);
    }

    OpenAiGateway delegate() {
        return delegate;
    }
}
