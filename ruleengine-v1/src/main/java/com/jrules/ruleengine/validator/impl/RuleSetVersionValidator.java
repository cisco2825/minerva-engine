package com.jrules.ruleengine.validator.impl;

import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationError;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import com.jrules.ruleengine.validator.RuleSetValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

/**
 * Validator for checking the version field in the rule set.
 * {@inheritDoc}
 */
@Slf4j
@Service("ruleset-version-validator")
public class RuleSetVersionValidator implements RuleSetValidator {

  /**
   * {@inheritDoc}
   */
  @Override
    public RuleSetValidationResult validate(RuleSet rule) {
    log.info("Rule file version validation started");
    RuleSetValidationResult rulesetValidationResult =
        new RuleSetValidationResult(new ArrayList<>());

      if (rule.getVersion() == null || rule.getVersion().trim().isEmpty()) {
            rulesetValidationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_VERSION_VALIDATION_ERROR,
                "Missing or empty file version"));
            return rulesetValidationResult;
      }

      // version format
      if (!rule.getVersion().matches("^\\d+\\.\\d+(\\.\\d+)?$")){
       rulesetValidationResult.addError(new RuleSetValidationError(RuleSetValidationErrorType.RULESET_VERSION_VALIDATION_ERROR,
                                                                   "Invalid format. Expected x.y or x.y.z"));
        return rulesetValidationResult;
      }

      return rulesetValidationResult;
    }
}
