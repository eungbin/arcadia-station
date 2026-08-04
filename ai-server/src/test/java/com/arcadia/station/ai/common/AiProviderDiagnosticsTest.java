package com.arcadia.station.ai.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(OutputCaptureExtension.class)
class AiProviderDiagnosticsTest {

    @Test
    void classifiesHttpFailuresWithoutLoggingSecrets(CapturedOutput output) {
        String responseBody = """
                {"error":{"status":"API_KEY_INVALID",\
                "message":"SECRET_PROVIDER_RESPONSE"}}
                """;
        HttpClientErrorException failure = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad request",
                HttpHeaders.EMPTY,
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        AiProviderDiagnostics.requestFailed(
                "GEMINI",
                "STRUCTURED_OUTPUT",
                AiPurpose.CASE_GENERATION,
                "gemini-test-model\u001b[31m\nFORGED\u202eline",
                0,
                Instant.now(),
                failure
        );

        assertThat(output.getOut())
                .contains("[AI-API][FAILURE] event=ai_provider_request_failed")
                .contains("provider=gemini operation=structured_output")
                .contains("purpose=CASE_GENERATION "
                        + "model=gemini-test-model_[31m_forged_line")
                .contains("httpStatus=400 failureCategory=AUTHENTICATION")
                .doesNotContain("SECRET_PROVIDER_RESPONSE")
                .doesNotContain("\u001b")
                .doesNotContain("\nFORGED")
                .doesNotContain("\u202e");
    }

    @Test
    void distinguishesTimeoutAndInvalidResponseFailures() {
        assertThat(AiProviderDiagnostics.failureView(
                new SocketTimeoutException("secret timeout detail"),
                0
        ).category()).isEqualTo("TIMEOUT");
        assertThat(AiProviderDiagnostics.failureView(
                new IllegalStateException("secret invalid JSON detail"),
                200
        ).category()).isEqualTo("INVALID_RESPONSE");
    }
}
