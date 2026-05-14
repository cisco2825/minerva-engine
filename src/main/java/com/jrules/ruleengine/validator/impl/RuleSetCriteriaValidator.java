package com.jrules.ruleengine.validator.impl;

import com.jrules.ruleengine.enums.DataSourceType;
import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import com.jrules.ruleengine.model.RuleSet.Criterion;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationError;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import com.jrules.ruleengine.validator.DataSourceValidator;
import com.jrules.ruleengine.validator.RuleSetValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validator for rule criteria in the loan marketplace rule engine.
 * {@inheritDoc}
 */
@Slf4j
@Service("ruleset-criteria-validator")
public class RuleSetCriteriaValidator implements RuleSetValidator {
    private static final Set<String> RESERVED_NAMES = Set.of("AND", "OR", "NOT");

    private final Map<DataSourceType, DataSourceValidator> validatorMap;

    public RuleSetCriteriaValidator(
            @Qualifier("inlineDataSourceValidator") DataSourceValidator inlineDataSourceValidator,
            @Qualifier("s3DataSourceValidator") DataSourceValidator s3DataSourceValidator) {
        this.validatorMap = Map.of(
            DataSourceType.INLINE, inlineDataSourceValidator,
            DataSourceType.S3, s3DataSourceValidator
        );
    }

    /**
     * Validates all criteria within a rule file.
     * {@inheritDoc}
     */
    @Override
    public RuleSetValidationResult validate(RuleSet rule) {
      log.info("Rule file criteria validation started");
      RuleSetValidationResult validationResult = new RuleSetValidationResult(new ArrayList<>());
      
      List<RuleSet.Criterion> criteria = rule.getCriteria();
        if (criteria == null || criteria.isEmpty()) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "No criteria defined"));
            return validationResult;
        }

        Set<String> usedNames = new HashSet<>();
        for (Criterion criterion : criteria) {
            validateCriterion(criterion, usedNames, validationResult);
        }

        return validationResult;
    }

    /**
     * Validates a single criterion through multiple validation steps.
     *
     * @param criterion the criterion to validate
     * @param usedNames set of already used criterion names for uniqueness checking
     */
    private void validateCriterion(RuleSet.Criterion criterion, Set<String> usedNames, RuleSetValidationResult validationResult) {
        if (!validateName(criterion, usedNames, validationResult)) return;
        if (!validateRequiredFields(criterion, validationResult)) return;
        
        DataType dataType = validateDataType(criterion, validationResult);
        if (dataType == null) return;
        
        Operator operator = validateOperator(criterion, dataType, validationResult);
        if (operator == null) return;
        
        validateValues(criterion, validationResult);
    }

    /**
     * Validates criterion name for emptiness, reserved words, and uniqueness.
     *
     * @param criterion the criterion whose name to validate
     * @param usedNames set of already used names
     * @return true if name is valid, false otherwise
     */
    private boolean validateName(Criterion criterion, Set<String> usedNames, RuleSetValidationResult validationResult) {
        String name = criterion.getName();
        if (name == null || name.trim().isEmpty()) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition with empty name found"));
            return false;
        }
        
        if(name.contains(" ")){
          validationResult.addError(new RuleSetValidationError(
              RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
              "Condition name '" + name + "' cannot have whitespaces"));
          return false;
        }

        if (checkIfReservedName(name)) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition name '" + name + "' is reserved (" + RESERVED_NAMES + " not allowed)"));
        }
        if (!usedNames.add(name)) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Duplicate criterion name: " + name));
        }
        return true;
    }

  private boolean checkIfReservedName(String name) {
      if(!RESERVED_NAMES.isEmpty()) {
        return RESERVED_NAMES.stream().anyMatch(
            reserved -> reserved.equalsIgnoreCase(name));
      }
      return false;
  }

  /**
     * Validates that all required fields (param, datatype, operator) are present.
     *
     * @param criterion the criterion to validate
     * @return true if all required fields are present, false otherwise
     */
    private boolean validateRequiredFields(Criterion criterion, RuleSetValidationResult validationResult) {
        String name = criterion.getName();
        boolean valid = true;
        
        if (isNullOrEmpty(criterion.getParam())) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition '" + name + "' missing 'param'"));
            valid = false;
        }
        if (criterion.getDatasource() == null) {
          validationResult.addError(new RuleSetValidationError(
              RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
              "Condition '" + name + "' missing 'datasource'"));
          return false;
        }
        if (isNullOrEmpty(criterion.getDatasource().getDatatype())) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition '" + name + "' missing 'datatype'"));
            valid = false;
        }
        if (isNullOrEmpty(criterion.getOperator())) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition '" + name + "' missing 'operator'"));
            valid = false;
        }
        return valid;
    }

    /**
     * Validates that the criterion's data type is supported.
     *
     * @param criterion the criterion to validate
     * @return the validated DataType enum, or null if invalid
     */
    private DataType validateDataType(Criterion criterion, RuleSetValidationResult validationResult) {
        DataType dataType = DataType.from(criterion.getDatasource().getDatatype());
        if (dataType == null) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition '" + criterion.getName() + "' has unsupported datatype: " + criterion.getDatasource().getDatatype()));
        }
        return dataType;
    }

    /**
     * Validates that the operator is supported and compatible with the data type.
     *
     * @param criterion the criterion to validate
     * @param dataType the validated data type for compatibility checking
     * @return the validated Operator enum, or null if invalid
     */
    private Operator validateOperator(Criterion criterion, DataType dataType, RuleSetValidationResult validationResult) {
        Operator operator = Operator.from(criterion.getOperator());
        if (operator == null) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition '" + criterion.getName() + "' has invalid operator: " + criterion.getOperator()));
            return null;
        }
        
        if (!operator.isValidFor(dataType)) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Operator '" + operator.token + "' not allowed for datatype " + criterion.getDatasource().getDatatype() + " in criterion '" + criterion.getName() + "'"));
        }
        return operator;
    }

    /**
     * Validates criterion values for correct count and data type compatibility.
     *
     * @param criterion the criterion containing values to validate
     */
    private void validateValues(RuleSet.Criterion criterion, RuleSetValidationResult validationResult) {
        DataSourceType type = criterion.getDatasource().getType();
        DataSourceValidator validator = validatorMap.get(type);
        
        if (validator == null) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition '" + criterion.getName() + "' has unsupported datasource type: " + type));
            return;
        }
        
        validator.validate(criterion, validationResult);
    }

    private static boolean isNullOrEmpty(String value) {
      return value == null || value.trim().isEmpty();
    }
}
