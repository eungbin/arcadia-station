package com.arcadia.station.infrastructure.openai;

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
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

public class OpenAiResponsesGateway implements OpenAiGateway {

    private final ObjectMapper objectMapper;
    private final ArcadiaAiProperties properties;
    private final JsonSchemaContractValidator schemaValidator;
    private final AiUsageRecorder usageRecorder;
    private final RestClient client;

    public OpenAiResponsesGateway(
            ObjectMapper objectMapper,
            ArcadiaAiProperties properties,
            RestClient.Builder builder,
            JsonSchemaContractValidator schemaValidator,
            AiUsageRecorder usageRecorder
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.schemaValidator = schemaValidator;
        this.usageRecorder = usageRecorder;
        ArcadiaAiProperties.ProviderSettings settings = properties.openai();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.caseGeneration().timeout());
        requestFactory.setReadTimeout(properties.caseGeneration().timeout());
        this.client = builder
                .baseUrl(settings.baseUrl())
                .defaultHeader("Authorization", "Bearer " + settings.apiKey())
                .requestFactory(requestFactory)
                .build();
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
        Map<String, Object> request = Map.of(
                "model", properties.openai().model(),
                "store", false,
                "input", List.of(
                        Map.of("role", "system", "content", prompt.system()),
                        Map.of("role", "user", "content", prompt.user())
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", schema.name(),
                                "strict", true,
                                "schema", schema.schema()
                        )
                )
        );
        Instant startedAt = Instant.now();
        int httpStatus = 0;
        AiProviderDiagnostics.requestStarted(
                "OPENAI",
                "STRUCTURED_OUTPUT",
                purpose,
                properties.openai().model(),
                "/responses"
        );
        try {
            ResponseEntity<JsonNode> responseEntity = client.post()
                    .uri("/responses")
                    .body(request)
                    .retrieve()
                    .toEntity(JsonNode.class);
            httpStatus = responseEntity.getStatusCode().value();
            JsonNode response = responseEntity.getBody();
            String output = extractOutputText(response);
            schemaValidator.validateOrThrow(output, schema);
            T result = objectMapper.readValue(output, responseType);
            long inputTokens = response.path("usage").path("input_tokens").asLong();
            long outputTokens = response.path("usage").path("output_tokens").asLong();
            usageRecorder.recordCall(
                    purpose,
                    Duration.between(startedAt, Instant.now()),
                    true,
                    inputTokens,
                    outputTokens
            );
            AiProviderDiagnostics.requestSucceeded(
                    "OPENAI",
                    "STRUCTURED_OUTPUT",
                    purpose,
                    properties.openai().model(),
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
                    "OPENAI",
                    "STRUCTURED_OUTPUT",
                    purpose,
                    properties.openai().model(),
                    httpStatus,
                    startedAt,
                    exception
            );
            throw translateFailure(
                    "OpenAI structured output could not be decoded",
                    exception
            );
        }
    }

    @Override
    public float[] createEmbedding(String input) {
        Map<String, Object> request = Map.of(
                "model", properties.openai().embeddingModel(),
                "input", input,
                "encoding_format", "float"
        );
        Instant startedAt = Instant.now();
        try {
            JsonNode response = client.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode values = response.path("data").path(0).path("embedding");
            if (!values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("OpenAI embedding response was empty");
            }
            float[] vector = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                vector[index] = (float) values.get(index).asDouble();
            }
            usageRecorder.recordCall(
                    AiPurpose.EMBEDDING,
                    Duration.between(startedAt, Instant.now()),
                    true,
                    response.path("usage").path("prompt_tokens").asLong(
                            response.path("usage").path("total_tokens").asLong()
                    ),
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
            throw translateFailure("OpenAI embedding request failed", exception);
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("OpenAI response was empty");
        }
        if ("incomplete".equals(response.path("status").asText())) {
            throw new IllegalStateException(
                    "OpenAI response incomplete: "
                            + response.path("incomplete_details").path("reason").asText()
            );
        }
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new IllegalStateException(
                            "OpenAI refused structured generation: "
                                    + content.path("refusal").asText()
                    );
                }
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("OpenAI response contained no output_text");
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
                    "OpenAI quota or rate limit was exhausted",
                    exception
            );
        }
        return new IllegalStateException(message, exception);
    }
}
