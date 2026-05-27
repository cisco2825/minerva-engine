package com.jrules.ruleengine.v2.storage.dto;

import lombok.Data;

import java.util.List;

@Data
public class ValidateExpressionsRequest {

    private List<ExpressionEntry> expressions;

    @Data
    public static class ExpressionEntry {
        /** Human-readable label shown in validation errors (e.g. "rule: age_check"). */
        private String label;

        /**
         * A plain expression string (RULE, BRANCH, OUTCOME outputExpression, MODEL EXPRESSION).
         * Mutually exclusive with {@link #template}.
         */
        private String expression;

        /**
         * A CUSTOM_OUTPUT template string (JSON-like with embedded expressions).
         * Mutually exclusive with {@link #expression}.
         */
        private String template;
    }
}
