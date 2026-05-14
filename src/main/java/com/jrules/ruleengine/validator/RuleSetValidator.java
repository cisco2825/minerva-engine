package com.jrules.ruleengine.validator;

import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationResult;

/**
 * Interface for validating rule files in the loan marketplace rule engine.
 * <p>
 * Implementations of this interface are responsible for ensuring that rule files
 * conform to the expected structure, contain valid data, and meet business requirements.
 * Validators should perform comprehensive checks including syntax validation,
 * semantic validation, and business rule compliance.
 * </p>
 *
 */
public interface RuleSetValidator {
  /**
   * Validates a rule file and returns a result containing any validation errors.
   * <p>
   * This method performs comprehensive validation of the provided rule file,
   * checking for structural integrity, data validity, and business rule compliance.
   * All validation errors should be collected and returned in the result rather
   * than throwing exceptions, allowing for batch error reporting.
   * </p>
   *
   * @param rule the rule file to validate. Must not be null.
   * @return a {@link com.jrules.ruleengine.model.RuleSetValidationResult} containing any validation errors found.
   *         Returns an empty result if no errors are detected.
   * @throws IllegalArgumentException if the rule parameter is null
   */
  RuleSetValidationResult validate(RuleSet rule);
}