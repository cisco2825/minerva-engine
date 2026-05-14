package com.jrules.ruleengine.exception;

import com.jrules.ruleengine.model.RuleSetValidationResult;

public class RuleSetValidationException extends RuntimeException {

    private final RuleSetValidationResult validationResult;

    public RuleSetValidationException(String message, RuleSetValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }

    public RuleSetValidationResult getValidationResult() {
        return validationResult;
    }
}
