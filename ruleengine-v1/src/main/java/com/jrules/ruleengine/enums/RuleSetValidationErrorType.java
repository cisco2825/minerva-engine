package com.jrules.ruleengine.enums;

/**
 * Enumeration of rule validation error types for categorizing validation failures.
 * <p>
 * Provides standardized error categories to help identify the source and nature
 * of validation failures during rule processing and evaluation.
 * </p>
 *
 * @author Shubham Thakur
 */
public enum RuleSetValidationErrorType {

  /**
   * Expression validation failure - errors in rule expression syntax or semantics. Includes parsing
   * errors, undefined criterion references, and logical inconsistencies.
   */
  RULESET_FORMULA_VALIDATION_FAILURE,

  /**
   * Rule criteria validation failure - errors in individual criterion definitions. Includes
   * missing fields, invalid operators, incompatible data types, and malformed values.
   */
  RULESET_CRITERIA_VALIDATION_FAILURE,

  /**
   * File version validation error - errors related to rule file versioning. Includes missing
   * version, unsupported version format, or version compatibility issues.
   */
  RULESET_VERSION_VALIDATION_ERROR,

  /**
   * Unexpected error - unforeseen validation failures not covered by other categories. Includes
   * system errors, validator exceptions, and other technical failures.
   */
  UNEXPECTED_ERROR;
}