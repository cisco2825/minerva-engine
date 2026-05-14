package com.jrules.ruleengine.validator.impl;

import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import com.jrules.ruleengine.model.RuleSetValidationError;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import com.jrules.ruleengine.utils.ParsingUtils;

import java.util.List;

/**
 * Common utility for validating datasource values.
 */
public class DataSourceValidatorHelper {
    
    /**
     * Validates that all values are parseable as the specified data type.
     *
     * @param values the values to validate
     * @param dataType the expected data type
     * @param operator the operator associated with the criterion
     * @param criterionName the criterion name for error messages
     * @param validationResult the result object to collect errors
     */
    public static void validateValueTypes(List<Object> values,
                                          DataType dataType,
                                          Operator operator,
                                          String criterionName,
                                          RuleSetValidationResult validationResult) {

      if (values == null || operator == null || dataType == null ) {
        return;
      }

      if (operator.expectedCount >= 0 && values.size() != operator.expectedCount) {
        validationResult.addError(new RuleSetValidationError(
            RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
            "Condition '" + criterionName + "' operator '" + operator.token + "' expects " + operator.expectedCount + " values, found " + values.size()));
        return;
      }

      for (Object value : values) {
            if (!ParsingUtils.isParsableAs(value, dataType)) {
                validationResult.addError(new RuleSetValidationError(
                    RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                    "Condition '" + criterionName + "' value '" + value + "' is not parseable as " + dataType));
            }
        }
    }
}
