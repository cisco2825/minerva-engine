package com.jrules.ruleengine.validator;

import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationResult;

/**
 * Interface for datasource-specific validators.
 */
public interface DataSourceValidator {
    
    /**
     * Validates datasource configuration and values.
     *
     * @param criterion the criterion containing datasource to validate
     * @param validationResult the result object to collect errors
     */
    void validate(RuleSet.Criterion criterion, RuleSetValidationResult validationResult);
}
