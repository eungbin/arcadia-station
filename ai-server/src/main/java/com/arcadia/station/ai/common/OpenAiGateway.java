package com.arcadia.station.ai.common;

public interface OpenAiGateway {

    <T> T generateStructured(
            AiPurpose purpose,
            String promptVersion,
            Object promptContext,
            JsonSchema schema,
            Class<T> responseType
    );

    float[] createEmbedding(String input);
}
