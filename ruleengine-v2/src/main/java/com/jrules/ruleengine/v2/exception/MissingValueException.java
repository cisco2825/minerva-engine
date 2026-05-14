package com.jrules.ruleengine.v2.exception;

/**
 * Thrown when a context path referenced in an expression is not present in the
 * evaluation context. The RuleChainEvaluator catches this and applies the
 * rule's onMissing policy (FAIL | PASS | SKIP).
 */
public class MissingValueException extends RuntimeException {

    private final String path;

    public MissingValueException(String path) {
        super("Missing value for context path: " + path);
        this.path = path;
    }

    public String getPath() { return path; }
}
