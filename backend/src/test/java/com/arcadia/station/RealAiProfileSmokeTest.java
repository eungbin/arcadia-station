package com.arcadia.station;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.client.AssistantClient;
import com.arcadia.station.client.CaseGenerationClient;
import com.arcadia.station.client.InterrogationClient;
import com.arcadia.station.client.RealAssistantClient;
import com.arcadia.station.client.RealCaseGenerationClient;
import com.arcadia.station.client.RealInterrogationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * "real-ai" 프로파일을 켰을 때 Fake 대신 Real 클라이언트만 하나씩 빈으로 등록되고
 * 컨텍스트가 정상 기동되는지 확인한다. 실제 AI 서버가 아직 없으므로(16.2절) 네트워크
 * 호출 없이 빈 구성만 검증하는 스모크 테스트다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("real-ai")
class RealAiProfileSmokeTest {

    @Autowired
    private CaseGenerationClient caseGenerationClient;

    @Autowired
    private InterrogationClient interrogationClient;

    @Autowired
    private AssistantClient assistantClient;

    @Test
    void real_ai_프로파일에서는_Real_클라이언트만_빈으로_등록된다() {
        assertThat(caseGenerationClient).isInstanceOf(RealCaseGenerationClient.class);
        assertThat(interrogationClient).isInstanceOf(RealInterrogationClient.class);
        assertThat(assistantClient).isInstanceOf(RealAssistantClient.class);
    }
}
