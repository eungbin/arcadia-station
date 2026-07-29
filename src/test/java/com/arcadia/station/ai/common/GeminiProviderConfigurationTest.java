package com.arcadia.station.ai.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.infrastructure.gemini.GeminiInteractionsGateway;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "arcadia.ai.enabled=true",
        "arcadia.ai.offline-mode=false",
        "arcadia.ai.provider=gemini",
        "arcadia.ai.gemini.api-key=test-only-key"
})
class GeminiProviderConfigurationTest {

    @Autowired
    private OpenAiGateway gateway;

    @Autowired
    private ArcadiaAiProperties properties;

    @Test
    void selectsGeminiGatewayAndModels() {
        assertThat(gateway).isInstanceOf(QuotaAwareAiGateway.class);
        assertThat(((QuotaAwareAiGateway) gateway).delegate())
                .isInstanceOf(GeminiInteractionsGateway.class);
        assertThat(properties.activeProvider())
                .isEqualTo(ArcadiaAiProperties.Provider.GEMINI);
        assertThat(properties.activeModel()).isEqualTo("gemini-3.6-flash");
        assertThat(properties.quotaCooldown()).isEqualTo(Duration.ofMinutes(10));
    }
}
