package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.arcadia.station.ai.common.OpenAiGateway;
import org.springframework.stereotype.Component;

@Component
public class StructuredCaseBlueprintGenerator implements CaseBlueprintGenerator {

    private final OpenAiGateway gateway;
    private final CasePromptAssembler promptAssembler;
    private final JsonSchemaRepository schemas;
    private final ArcadiaAiProperties properties;

    public StructuredCaseBlueprintGenerator(
            OpenAiGateway gateway,
            CasePromptAssembler promptAssembler,
            JsonSchemaRepository schemas,
            ArcadiaAiProperties properties
    ) {
        this.gateway = gateway;
        this.promptAssembler = promptAssembler;
        this.schemas = schemas;
        this.properties = properties;
    }

    @Override
    public CaseBlueprint generate(CaseGenerationRequest request) {
        return gateway.generateStructured(
                AiPurpose.CASE_GENERATION,
                properties.caseGeneration().promptVersion(),
                promptAssembler.assemble(request),
                schemas.get("case_blueprint"),
                CaseBlueprint.class
        );
    }
}
