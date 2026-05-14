package com.jrules.ruleengine.model;

import java.util.Collections;
import java.util.List;

/**
 * Contains the result of rule validation with any errors found.
 * Provides methods to check validity and manage validation errors.
 */
public class RuleSetValidationResult {
  private final List<RuleSetValidationError> errors;

  /**
   * Creates a validation result with the given errors.
   * @param errors list of validation errors
   */
  public RuleSetValidationResult(List<RuleSetValidationError> errors) {
    this.errors = errors;
  }

  /**
   * Checks if validation passed without errors.
   * @return true if no validation errors exist
   */
  public boolean isValid() {
    return this.errors.isEmpty();
  }

  /**
   * Adds a validation error to the result.
   * @param error the validation error to add
   */
  public void addError(RuleSetValidationError error) {
    this.errors.add(error);
  }

  /**
   * Adds multiple validation errors to the result.
   * @param errs list of validation errors to add
   */
  public void addErrors(List<RuleSetValidationError> errs) {
    if (errs != null) this.errors.addAll(errs);
  }

  /**
   * Returns an immutable list of validation errors.
   * @return unmodifiable list of validation errors
   */
  public List<RuleSetValidationError> getErrors() {
    return Collections.unmodifiableList(this.errors);
  }

  @Override
  public String toString() {
    return "RuleValidationResult{valid=" + isValid() + ", errors=" + this.errors + "}";
  }
}