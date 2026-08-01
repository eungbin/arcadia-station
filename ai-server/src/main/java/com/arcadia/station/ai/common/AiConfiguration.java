package com.arcadia.station.ai.common;

import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import com.arcadia.station.infrastructure.gemini.GeminiInteractionsGateway;
import com.arcadia.station.infrastructure.openai.OpenAiResponsesGateway;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJsonContracts() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    OpenAiGateway openAiGateway(
            ObjectMapper objectMapper,
            ArcadiaAiProperties properties,
            RestClient.Builder builder,
            FallbackCaseProvider fallbackCaseProvider,
            JsonSchemaContractValidator schemaValidator,
            AiUsageRecorder usageRecorder
    ) {
        if (!properties.enabled()
                || properties.offlineMode()
                || !properties.hasActiveApiKey()) {
            return new FakeOpenAiGateway(fallbackCaseProvider);
        }
        OpenAiGateway providerGateway = switch (properties.activeProvider()) {
            case GEMINI -> new GeminiInteractionsGateway(
                    objectMapper,
                    properties,
                    builder,
                    schemaValidator,
                    usageRecorder
            );
            case OPENAI -> new OpenAiResponsesGateway(
                    objectMapper,
                    properties,
                    builder,
                    schemaValidator,
                    usageRecorder
            );
        };
        return new QuotaAwareAiGateway(
                providerGateway,
                properties.activeProvider().name(),
                properties.quotaCooldown(),
                usageRecorder
        );
    }

    @Bean(name = "caseGenerationExecutor")
    TaskExecutor caseGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("case-generation-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
