package com.arcadia.station.ai.validation;

import java.util.List;

public record CaseValidationResult(List<ValidationIssue> issues) {

    public boolean valid() {
        return issues.isEmpty();
    }
}
