package com.jrules.ruleengine.model;

import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@AllArgsConstructor
public class RuleSetValidationError {
    private RuleSetValidationErrorType errorType;
    private String message;
}
