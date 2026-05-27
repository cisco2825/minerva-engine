package com.jrules.ruleengine.v2.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ValidateExpressionsResponse {

    private boolean valid;
    private List<ValidationError> errors;

    @Data
    @AllArgsConstructor
    public static class ValidationError {
        /** Label from the corresponding request entry. */
        private String label;
        /** Human-readable parse error message. */
        private String message;
        /** 1-based line number where the error occurred (null for template errors). */
        private Integer line;
        /** 1-based column number where the error occurred (null for template errors). */
        private Integer column;
    }
}
