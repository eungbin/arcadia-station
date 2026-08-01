package com.arcadia.station.ai.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaRepository {

    private static final Map<String, String> PATHS = Map.of(
            "case_blueprint", "ai/schema/case-blueprint.schema.json",
            "npc_turn", "ai/schema/npc-turn-response.schema.json",
            "rag_summary", "ai/schema/rag-summary-response.schema.json",
            "world_template", "ai/schema/world-template.schema.json",
            "mystery_rule_template", "ai/schema/mystery-rule-template.schema.json"
    );

    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    public JsonSchemaRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonSchema get(String name) {
        return cache.computeIfAbsent(name, this::load);
    }

    private JsonSchema load(String name) {
        String path = PATHS.get(name);
        if (path == null) {
            throw new IllegalArgumentException("Unknown schema: " + name);
        }
        try {
            JsonNode schema = objectMapper.readTree(new ClassPathResource(path).getInputStream());
            return new JsonSchema(name, schema);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load JSON Schema: " + path, exception);
        }
    }
}
