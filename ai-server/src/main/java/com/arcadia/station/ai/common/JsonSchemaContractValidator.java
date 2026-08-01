package com.arcadia.station.ai.common;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaContractValidator {

    public List<com.networknt.schema.Error> validate(String json, JsonSchema jsonSchema) {
        String schemaLocation = "https://arcadia.local/schema/" + jsonSchema.name();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(Map.of(
                        schemaLocation,
                        jsonSchema.schema().toString()
                ))
        );
        Schema schema = registry.getSchema(SchemaLocation.of(schemaLocation));
        return schema.validate(json, InputFormat.JSON);
    }

    public void validateOrThrow(String json, JsonSchema jsonSchema) {
        List<com.networknt.schema.Error> errors = validate(json, jsonSchema);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "JSON Schema validation failed for "
                            + jsonSchema.name()
                            + ": "
                            + errors
            );
        }
    }
}
