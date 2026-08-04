package com.arcadia.station.infrastructure.gemini;

import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.AiProviderDiagnostics;
import com.arcadia.station.ai.common.AiQuotaExceededException;
import com.arcadia.station.ai.common.AiUsageRecorder;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.JsonSchema;
import com.arcadia.station.ai.common.JsonSchemaContractValidator;
import com.arcadia.station.ai.common.OpenAiGateway;
import com.arcadia.station.ai.common.StructuredPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

public class GeminiInteractionsGateway implements OpenAiGateway {

    private static final int EMBEDDING_DIMENSIONS = 768;

    private final ObjectMapper objectMapper;
    private final ArcadiaAiProperties properties;
    private final JsonSchemaContractValidator schemaValidator;
    private final AiUsageRecorder usageRecorder;
    private final RestClient client;

    public GeminiInteractionsGateway(
            ObjectMapper objectMapper,
            ArcadiaAiProperties properties,
            RestClient.Builder builder,
            JsonSchemaContractValidator schemaValidator,
            AiUsageRecorder usageRecorder
    ) {
        this(
                objectMapper,
                properties,
                configuredBuilder(properties, builder).build(),
                schemaValidator,
                usageRecorder
        );
    }

    GeminiInteractionsGateway(
            ObjectMapper objectMapper,
            ArcadiaAiProperties properties,
            RestClient client,
            JsonSchemaContractValidator schemaValidator,
            AiUsageRecorder usageRecorder
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.schemaValidator = schemaValidator;
        this.usageRecorder = usageRecorder;
        this.client = client;
    }

    static RestClient.Builder configuredBuilder(
            ArcadiaAiProperties properties,
            RestClient.Builder builder
    ) {
        ArcadiaAiProperties.ProviderSettings settings = properties.gemini();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.caseGeneration().timeout());
        requestFactory.setReadTimeout(properties.caseGeneration().timeout());
        return builder
                .baseUrl(settings.baseUrl())
                .defaultHeader("x-goog-api-key", settings.apiKey())
                .requestFactory(requestFactory);
    }

    @Override
    public <T> T generateStructured(
            AiPurpose purpose,
            String promptVersion,
            Object promptContext,
            JsonSchema schema,
            Class<T> responseType
    ) {
        StructuredPrompt prompt = promptContext instanceof StructuredPrompt structured
                ? structured
                : new StructuredPrompt(
                        "Return only data that follows the supplied schema.",
                        write(promptContext)
                );
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        responseFormat.put("schema", schema.schema());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.gemini().model());
        request.put("system_instruction", prompt.system());
        request.put("input", prompt.user());
        request.put("response_format", responseFormat);
        request.put("store", false);

        Instant startedAt = Instant.now();
        int httpStatus = 0;
        AiProviderDiagnostics.requestStarted(
                "GEMINI",
                "STRUCTURED_OUTPUT",
                purpose,
                properties.gemini().model(),
                "/interactions"
        );
        try {
            ResponseEntity<JsonNode> responseEntity = client.post()
                    .uri("/interactions")
                    .body(request)
                    .retrieve()
                    .toEntity(JsonNode.class);
            httpStatus = responseEntity.getStatusCode().value();
            JsonNode response = responseEntity.getBody();
            String output = extractOutputText(response);
            schemaValidator.validateOrThrow(output, schema);
            T result = objectMapper.readValue(output, responseType);
            long inputTokens = response.path("usage").path("total_input_tokens").asLong();
            long outputTokens = response.path("usage").path("total_output_tokens").asLong();
            usageRecorder.recordCall(
                    purpose,
                    Duration.between(startedAt, Instant.now()),
                    true,
                    inputTokens,
                    outputTokens
            );
            AiProviderDiagnostics.requestSucceeded(
                    "GEMINI",
                    "STRUCTURED_OUTPUT",
                    purpose,
                    properties.gemini().model(),
                    httpStatus,
                    startedAt,
                    inputTokens,
                    outputTokens
            );
            return result;
        } catch (Exception exception) {
            usageRecorder.recordCall(
                    purpose,
                    Duration.between(startedAt, Instant.now()),
                    false,
                    0,
                    0
            );
            AiProviderDiagnostics.requestFailed(
                    "GEMINI",
                    "STRUCTURED_OUTPUT",
                    purpose,
                    properties.gemini().model(),
                    httpStatus,
                    startedAt,
                    exception
            );
            throw translateFailure(
                    "Gemini structured output could not be decoded",
                    exception
            );
        }
    }

    @Override
    public float[] createEmbedding(String input) {
        String model = normalizeModelName(properties.gemini().embeddingModel());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", "models/" + model);
        request.put("content", Map.of(
                "parts", List.of(Map.of("text", input))
        ));
        request.put("output_dimensionality", EMBEDDING_DIMENSIONS);

        Instant startedAt = Instant.now();
        try {
            JsonNode response = client.post()
                    .uri("/models/{model}:embedContent", model)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode values = response == null
                    ? null
                    : response.path("embedding").path("values");
            if (values == null || !values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("Gemini embedding response was empty");
            }
            float[] vector = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                vector[index] = (float) values.get(index).asDouble();
            }
            usageRecorder.recordCall(
                    AiPurpose.EMBEDDING,
                    Duration.between(startedAt, Instant.now()),
                    true,
                    response.path("usageMetadata").path("promptTokenCount").asLong(),
                    0
            );
            return vector;
        } catch (RuntimeException exception) {
            usageRecorder.recordCall(
                    AiPurpose.EMBEDDING,
                    Duration.between(startedAt, Instant.now()),
                    false,
                    0,
                    0
            );
            throw translateFailure("Gemini embedding request failed", exception);
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("Gemini response was empty");
        }
        String status = response.path("status").asText();
        if (!"completed".equals(status)) {
            String detail = response.path("error").path("message").asText();
            throw new IllegalStateException(
                    "Gemini interaction status was " + status
                            + (detail.isBlank() ? "" : ": " + detail)
            );
        }
        String outputText = response.path("output_text").asText();
        if (!outputText.isBlank()) {
            return outputText;
        }
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) {
                continue;
            }
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asText())
                        && !content.path("text").asText().isBlank()) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("Gemini response contained no model text");
    }

    private String normalizeModelName(String model) {
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize prompt context", exception);
        }
    }

    private RuntimeException translateFailure(String message, Exception exception) {
        if (AiQuotaExceededException.isQuotaFailure(exception)) {
            return new AiQuotaExceededException(
                    "Gemini quota or rate limit was exhausted",
                    exception
            );
        }
        return new IllegalStateException(message, exception);
    }
}
