package com.jrules.ruleengine.v2.exception;

/**
 * Thrown when a context path referenced in an expression is not present in the
 * evaluation context. Callers are expected to surface this as an actionable error
 * that identifies the rule and node where the field is missing.
 * Use {@code cantDecideExpression} on the rule to explicitly handle fields that
 * may legitimately be absent.
 */
public class MissingValueException extends RuntimeException {

    private final String path;

    public MissingValueException(String path) {
        super("Missing value for context path: " + path);
        this.path = path;
    }

    public String getPath() { return path; }
}
