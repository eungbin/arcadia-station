package com.arcadia.station.ai.template;

import com.arcadia.station.ai.common.JsonSchemaContractValidator;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.arcadia.station.ai.validation.MysteryRuleTemplateValidator;
import com.arcadia.station.ai.validation.ValidationIssue;
import com.arcadia.station.ai.validation.WorldTemplateValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TemplateRepository {

    private final WorldTemplate world;
    private final MysteryRuleTemplate rules;

    public TemplateRepository(
            ObjectMapper objectMapper,
            WorldTemplateValidator worldValidator,
            MysteryRuleTemplateValidator ruleValidator,
            JsonSchemaRepository schemas,
            JsonSchemaContractValidator schemaValidator
    ) {
        String worldJson = readText("ai/world/arcadia-world-v1.json");
        schemaValidator.validateOrThrow(worldJson, schemas.get("world_template"));
        this.world = read(
                objectMapper,
                worldJson,
                WorldTemplate.class
        );
        List<ValidationIssue> worldIssues = worldValidator.validate(world);
        if (!worldIssues.isEmpty()) {
            throw new IllegalStateException("Invalid WorldTemplate: " + worldIssues);
        }

        String rulesJson = readText("ai/rules/arcadia-mystery-rules-v1.json");
        schemaValidator.validateOrThrow(rulesJson, schemas.get("mystery_rule_template"));
        this.rules = read(
                objectMapper,
                rulesJson,
                MysteryRuleTemplate.class
        );
        List<ValidationIssue> ruleIssues = ruleValidator.validate(world, rules);
        if (!ruleIssues.isEmpty()) {
            throw new IllegalStateException("Invalid MysteryRuleTemplate: " + ruleIssues);
        }
    }

    public WorldTemplate world() {
        return world;
    }

    public MysteryRuleTemplate rules() {
        return rules;
    }

    private <T> T read(ObjectMapper mapper, String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot decode required AI resource", exception);
        }
    }

    private String readText(String path) {
        try {
            return new String(
                    new ClassPathResource(path).getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load required AI resource: " + path, exception);
        }
    }
}
