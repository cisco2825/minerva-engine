package com.jrules.ruleengine.v2.validator;

import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.model.result.ValidationResult;

/**
 * Orchestrates all validation stages in order:
 *   Stage 0 — Request structure
 *   Stage 1 — UDFs
 *   Stage 2 — Lookups
 *   Stage 3 — Tables
 *   Stage 4 — Policy structure
 *   Stage 5 — Rule structure (RULE_CHAIN)
 *   Stage 6 — Expression validation per rule
 *
 * All errors are collected before returning — never stops at first failure.
 */
public interface RequestValidator {

    ValidationResult validate(ValidationRequest request);
}
