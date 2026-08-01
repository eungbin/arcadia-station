package com.arcadia.station.ai.validation;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultCaseBlueprintValidator implements CaseBlueprintValidator {

    private final List<CaseBlueprintCheck> checks;

    public DefaultCaseBlueprintValidator(List<CaseBlueprintCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    @Override
    public CaseValidationResult validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        if (blueprint == null) {
            return new CaseValidationResult(List.of(
                    ValidationIssue.of("SCHEMA_INVALID", "$", "CaseBlueprint is null")
            ));
        }
        return new CaseValidationResult(checks.stream()
                .flatMap(check -> check.validate(world, rules, blueprint).stream())
                .toList());
    }
}
