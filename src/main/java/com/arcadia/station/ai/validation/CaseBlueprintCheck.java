package com.arcadia.station.ai.validation;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import java.util.List;

public interface CaseBlueprintCheck {

    List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    );
}
