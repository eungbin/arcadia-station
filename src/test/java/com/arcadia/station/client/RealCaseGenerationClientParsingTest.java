package com.arcadia.station.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.config.AiServerProperties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 서버 회신(2026-07-29) 3.3절: 실제 READY 응답의 generation 객체에는 우리 DTO에 없는
 * blueprintId/seed/worldTemplate/ruleTemplate 필드가 더 있다. Jackson 기본 동작(알 수 없는
 * 속성 무시)으로 역직렬화가 깨지지 않는지, AI 서버팀이 실제로 준 예시 응답으로 직접 확인한다.
 */
class RealCaseGenerationClientParsingTest {

    @Test
    void 실제_READY_응답의_추가_필드가_있어도_파싱이_깨지지_않는다() throws Exception {
        String rawBody;
        try (InputStream in = getClass().getResourceAsStream("/ai-server/internal-case-ready.response.json")) {
            rawBody = new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
        }

        AiServerProperties properties = new AiServerProperties("http://localhost:0", "test-key", null, null, null);
        RealCaseGenerationClient client = new RealCaseGenerationClient(properties, new ObjectMapper());

        CaseGenerationStatus status = client.parseStatus(rawBody);

        assertThat(status.status()).isEqualTo("READY");
        assertThat(status.generation()).isNotNull();
        assertThat(status.generation().caseBlueprint().blueprintId()).isEqualTo("CASE-BACKENDCONTRACT001");
        assertThat(status.generation().caseBlueprint().culpritId()).isEqualTo("SOPHIA");
        assertThat(status.generation().generationSource()).isEqualTo("FALLBACK");
        assertThat(status.generation().rawCaseBlueprintJson()).contains("\"blueprintId\"");
    }
}
