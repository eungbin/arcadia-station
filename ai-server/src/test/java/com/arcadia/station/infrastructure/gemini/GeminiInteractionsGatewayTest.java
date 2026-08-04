package com.arcadia.station.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.AiQuotaExceededException;
import com.arcadia.station.ai.common.AiUsageRecorder;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.JsonSchema;
import com.arcadia.station.ai.common.JsonSchemaContractValidator;
import com.arcadia.station.ai.common.StructuredPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class GeminiInteractionsGatewayTest {

    private ObjectMapper objectMapper;
    private MockRestServiceServer server;
    private GeminiInteractionsGateway gateway;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ArcadiaAiProperties properties = new ArcadiaAiProperties(
                true,
                false,
                ArcadiaAiProperties.Provider.GEMINI,
                new ArcadiaAiProperties.ProviderSettings(
                        "",
                        "openai-test-model",
                        "openai-test-embedding",
                        "https://api.openai.test/v1"
                ),
                new ArcadiaAiProperties.ProviderSettings(
                        "gemini-test-key",
                        "gemini-3.6-flash",
                        "gemini-embedding-2",
                        "https://generativelanguage.googleapis.com/v1beta"
                ),
                Duration.ofMinutes(10),
                new ArcadiaAiProperties.CaseGeneration(
                        Duration.ofSeconds(2),
                        0,
                        "test-prompt"
                ),
                new ArcadiaAiProperties.Npc(Duration.ofSeconds(2), 2),
                new ArcadiaAiProperties.Rag(2, 0.1)
        );
        RestClient.Builder builder = GeminiInteractionsGateway.configuredBuilder(
                properties,
                RestClient.builder()
        );
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GeminiInteractionsGateway(
                objectMapper,
                properties,
                builder.build(),
                new JsonSchemaContractValidator(),
                new AiUsageRecorder(new SimpleMeterRegistry())
        );
    }

    @Test
    void decodesAndValidatesStructuredInteractionOutput(
            CapturedOutput output
    ) throws Exception {
        JsonSchema schema = new JsonSchema(
                "test_response",
                objectMapper.readTree("""
                        {
                          "type": "object",
                          "additionalProperties": false,
                          "properties": {
                            "value": { "type": "string" }
                          },
                          "required": ["value"]
                        }
                        """)
        );
        server.expect(once(), requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/interactions"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-test-key"))
                .andExpect(jsonPath("$.model").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.system_instruction").value("System rule"))
                .andExpect(jsonPath("$.response_format.mime_type")
                        .value("application/json"))
                .andExpect(jsonPath("$.response_format.schema.required[0]")
                        .value("value"))
                .andRespond(withSuccess("""
                        {
                          "status": "completed",
                          "steps": [
                            {
                              "type": "model_output",
                              "content": [
                                {
                                  "type": "text",
                                  "text": "{\\"value\\":\\"generated\\"}"
                                }
                              ]
                            }
                          ],
                          "usage": {
                            "total_input_tokens": 12,
                            "total_output_tokens": 5
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        TestResponse response = gateway.generateStructured(
                AiPurpose.CASE_GENERATION,
                "test-prompt",
                new StructuredPrompt("System rule", "User request"),
                schema,
                TestResponse.class
        );

        assertThat(response.value()).isEqualTo("generated");
        assertThat(output.getOut())
                .contains("[AI-API][REQUEST] event=ai_provider_request_started "
                        + "provider=gemini operation=structured_output "
                        + "purpose=CASE_GENERATION model=gemini-3.6-flash "
                        + "endpoint=/interactions")
                .contains("[AI-API][SUCCESS] event=ai_provider_request_succeeded "
                        + "provider=gemini operation=structured_output "
                        + "purpose=CASE_GENERATION model=gemini-3.6-flash "
                        + "httpStatus=200")
                .doesNotContain("gemini-test-key");
        server.verify();
    }

    @Test
    void createsGeminiEmbeddingWithFixedDimensionsRequest() {
        server.expect(once(), requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/"
                                + "models/gemini-embedding-2:embedContent"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-test-key"))
                .andExpect(jsonPath("$.model").value("models/gemini-embedding-2"))
                .andExpect(jsonPath("$.content.parts[0].text").value("evidence text"))
                .andExpect(jsonPath("$.output_dimensionality").value(768))
                .andRespond(withSuccess("""
                        {
                          "embedding": {
                            "values": [0.125, -0.5, 0.75]
                          },
                          "usageMetadata": {
                            "promptTokenCount": 3
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        float[] embedding = gateway.createEmbedding("evidence text");

        assertThat(embedding).containsExactly(0.125f, -0.5f, 0.75f);
        server.verify();
    }

    @Test
    void mapsResourceExhaustedResponseToQuotaException() {
        server.expect(once(), requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/"
                                + "models/gemini-embedding-2:embedContent"
                ))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "code": 429,
                                    "status": "RESOURCE_EXHAUSTED",
                                    "message": "Quota exhausted"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> gateway.createEmbedding("quota test"))
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessageContaining("quota");
        server.verify();
    }

    private record TestResponse(String value) {}
}
