package com.arcadia.station.ai.common;

import com.arcadia.station.ai.casegen.FallbackCaseProvider;
import com.arcadia.station.infrastructure.openai.OpenAiResponsesGateway;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
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
                || properties.apiKey() == null
                || properties.apiKey().isBlank()) {
            return new FakeOpenAiGateway(fallbackCaseProvider);
        }
        return new OpenAiResponsesGateway(
                objectMapper,
                properties,
                builder,
                schemaValidator,
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
