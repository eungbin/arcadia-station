package com.arcadia.station.client;

import com.arcadia.station.client.dto.CaseGenerationAck;
import com.arcadia.station.client.dto.CaseGenerationStatus;
import com.arcadia.station.client.dto.GenerationResult;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 AI 서버 없이 4장 계약을 흉내내는 Fake 구현체. 항상 동일한 데모 사건을 즉시(동기) READY로 반환한다.
 * 15장 3단계: 실제 AI 서버 연동(8번 작업)으로 교체되기 전까지 전체 플로우를 완주시키는 용도.
 */
@Component
public class FakeCaseGenerationClient implements CaseGenerationClient {

    private static final String FIXTURE_PATH = "/fixtures/sample-case-blueprint.json";

    private final CaseBlueprint caseBlueprint;
    private final String rawCaseBlueprintJson;
    private final String blueprintSha256;

    public FakeCaseGenerationClient(ObjectMapper objectMapper) {
        byte[] rawBytes = readFixtureBytes();
        this.rawCaseBlueprintJson = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
        this.caseBlueprint = objectMapper.readValue(rawBytes, CaseBlueprint.class);
        this.blueprintSha256 = sha256Hex(rawBytes);
    }

    @Override
    public CaseGenerationAck requestCase(String aiCaseRequestId, String seed) {
        return new CaseGenerationAck(aiCaseRequestId, "CREATING", "/internal/v1/cases/" + aiCaseRequestId);
    }

    @Override
    public CaseGenerationStatus pollStatus(String aiCaseRequestId) {
        GenerationResult generation = new GenerationResult(
                caseBlueprint,
                rawCaseBlueprintJson,
                blueprintSha256,
                1,
                "AI",
                "fake-model",
                "v1",
                Instant.now(),
                Instant.now());
        return new CaseGenerationStatus(aiCaseRequestId, "READY", generation, null);
    }

    private static byte[] readFixtureBytes() {
        try (InputStream in = FakeCaseGenerationClient.class.getResourceAsStream(FIXTURE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Fake case blueprint fixture not found: " + FIXTURE_PATH);
            }
            return StreamUtils.copyToByteArray(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fake case blueprint fixture", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
