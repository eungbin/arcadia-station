package com.arcadia.station.ai.validation;

public record ValidationIssue(String code, String path, String message) {

    public static ValidationIssue of(String code, String path, String message) {
        return new ValidationIssue(code, path, message);
    }
}
