package com.jrules.ruleengine.v2.exception;

import com.jrules.ruleengine.v2.model.result.ValidationResult;
import lombok.Getter;

@Getter
public class V2ValidationException extends RuntimeException {

    private final ValidationResult validationResult;

    public V2ValidationException(String message, ValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }
}
