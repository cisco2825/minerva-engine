package com.jrules.ruleengine.v2.validator;

import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.model.result.ValidationResult;
import com.jrules.ruleengine.v2.model.result.ValidationResult.TokenPosition;
import com.jrules.ruleengine.v2.model.result.ValidationResult.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable accumulator passed through all validation stages.
 * Errors and warnings are collected — never fails fast.
 */
public class ValidationContext {

    private final ValidationRequest request;
    private final List<ValidationError> errors   = new ArrayList<>();
    private final List<ValidationError> warnings = new ArrayList<>();

    public ValidationContext(ValidationRequest request) {
        this.request = request;
    }

    public ValidationRequest getRequest() { return request; }
    public boolean hasErrors() { return !errors.isEmpty(); }

    public void error(String code, String stage, String location, String message) {
        error(code, stage, location, message, null, null);
    }

    public void error(String code, String stage, String location, String message, String suggestion) {
        error(code, stage, location, message, suggestion, null);
    }

    public void error(String code, String stage, String location, String message,
                      String suggestion, TokenPosition position) {
        errors.add(ValidationError.builder()
                .code(code)
                .severity("ERROR")
                .stage(stage)
                .location(location)
                .message(message)
                .suggestion(suggestion)
                .position(position)
                .build());
    }

    public void warning(String code, String stage, String location, String message) {
        warnings.add(ValidationError.builder()
                .code(code)
                .severity("WARNING")
                .stage(stage)
                .location(location)
                .message(message)
                .build());
    }

    public ValidationResult toResult() {
        return ValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(List.copyOf(errors))
                .warnings(List.copyOf(warnings))
                .build();
    }
}
