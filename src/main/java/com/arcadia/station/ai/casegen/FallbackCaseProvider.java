package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.common.JsonSchemaContractValidator;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class FallbackCaseProvider {

    private final ObjectMapper objectMapper;
    private final JsonNode template;

    public FallbackCaseProvider(
            ObjectMapper objectMapper,
            JsonSchemaRepository schemas,
            JsonSchemaContractValidator schemaValidator
    ) {
        this.objectMapper = objectMapper;
        try {
            String json = new String(
                    new ClassPathResource("ai/fallback/sophia-safe-v1.json")
                            .getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            schemaValidator.validateOrThrow(json, schemas.get("case_blueprint"));
            this.template = objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load validated fallback case", exception);
        }
    }

    public CaseBlueprint forSession(String sessionId, String seed) {
        ObjectNode copy = template.deepCopy();
        copy.put("blueprintId", "CASE-" + normalize(sessionId));
        copy.put("seed", seed);
        try {
            return objectMapper.treeToValue(copy, CaseBlueprint.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Fallback case is not decodable", exception);
        }
    }

    private String normalize(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        return normalized.substring(0, Math.min(normalized.length(), 20));
    }
}
