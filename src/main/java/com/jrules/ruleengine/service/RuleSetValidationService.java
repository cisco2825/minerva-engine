package com.jrules.ruleengine.service;

import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.validator.*;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for validating rule sets using multiple validators.
 * Orchestrates validation process and aggregates results from all configured validators.
 */
@Slf4j
@Service
public class RuleSetValidationService {

    private final List<RuleSetValidator> validators;

    public RuleSetValidationService(final List<RuleSetValidator> validators) {
      this.validators = validators;
    }

    /**
     * Validates a rule set using all configured validators.
     * Executes each validator and aggregates all validation errors into a single result.
     * 
     * @param rule the rule set to validate
     * @return validation result containing all errors found by validators
     */
    public RuleSetValidationResult validate(RuleSet rule) {
      log.info("RuleSet validation started");
      RuleSetValidationResult validationResult = new RuleSetValidationResult(new ArrayList<>());
      for (RuleSetValidator v : validators) {
        try {
          validationResult.addErrors(v.validate(rule).getErrors());

          // if validation failure, return result
          if(!validationResult.isValid()){
            return validationResult;
          }

        } catch (Exception ex) {
          validationResult.addError(
              new com.jrules.ruleengine.model.RuleSetValidationError(
                  RuleSetValidationErrorType.UNEXPECTED_ERROR,
                  "Validator " + v.getClass().getSimpleName() + " failed: " + ex.getMessage())
          );
          return validationResult;
        }
      }

      log.info("RuleSet validation completed");
      return validationResult;
    }
}