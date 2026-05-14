package com.jrules.ruleengine.validator.impl;

import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationError;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import com.jrules.ruleengine.validator.DataSourceValidator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("inlineDataSourceValidator")
public class InlineDataSourceValidator implements DataSourceValidator {
    
    @Override
    public void validate(RuleSet.Criterion criterion, RuleSetValidationResult validationResult) {
        List<Object> values = criterion.getDatasource().getValue();
        DataType dataType = DataType.from(criterion.getDatasource().getDatatype());
        Operator operator = Operator.from(criterion.getOperator());
        String name = criterion.getName();
        
        if (values == null || values.isEmpty()) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Condition '" + name + "' inline datasource missing values"));
            return;
        }
        
        DataSourceValidatorHelper.validateValueTypes(values, dataType, operator, name,
                                                     validationResult);
    }
}
