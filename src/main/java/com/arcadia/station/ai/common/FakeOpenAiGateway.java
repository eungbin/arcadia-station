package com.arcadia.station.ai.common;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class FakeOpenAiGateway implements OpenAiGateway {

    private final FallbackCaseProvider fallbackCaseProvider;

    public FakeOpenAiGateway(FallbackCaseProvider fallbackCaseProvider) {
        this.fallbackCaseProvider = fallbackCaseProvider;
    }

    @Override
    public <T> T generateStructured(
            AiPurpose purpose,
            String promptVersion,
            Object promptContext,
            JsonSchema schema,
            Class<T> responseType
    ) {
        if (responseType == CaseBlueprint.class) {
            StructuredPrompt prompt = (StructuredPrompt) promptContext;
            String seed = prompt.user().replaceAll("(?s).*\"seed\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            return responseType.cast(fallbackCaseProvider.forSession("fake", seed));
        }
        throw new IllegalStateException("Fake response not configured for " + responseType.getName());
    }

    @Override
    public float[] createEmbedding(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            float[] vector = new float[32];
            for (int index = 0; index < vector.length; index++) {
                vector[index] = (digest[index] & 0xff) / 255.0f;
            }
            return vector;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot build deterministic fake embedding", exception);
        }
    }
}
