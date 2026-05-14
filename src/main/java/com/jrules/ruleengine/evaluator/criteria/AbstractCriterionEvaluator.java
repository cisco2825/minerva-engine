package com.jrules.ruleengine.evaluator.criteria;

import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.service.DataSourceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Slf4j
public abstract class AbstractCriterionEvaluator<T> implements CriterionEvaluator {

    @Autowired
    private DataSourceResolver dataSourceResolver;

    @Override
    public final boolean evaluate(RuleSet.Criterion criterion, Object actualValue) {
        if (actualValue == null) {
            log.debug("Actual value is null for criterion: {}", criterion.getName());
            return false;
        }

        try {
            T parsedValue = parseValue(actualValue);
            List<Object> expectedValues = dataSourceResolver.resolveValues(criterion);
            Operator operator = Operator.from(criterion.getOperator());
            
            validateExpectedValues(expectedValues, operator);
            
            return evaluateByOperator(parsedValue, expectedValues, operator);
            
        } catch (Exception e) {
            log.error("Failed to parse value: {} for criterion: {}", actualValue, criterion.getName(), e);
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    protected abstract T parseValue(Object value);
    protected abstract boolean evaluateByOperator(T actual, List<Object> expectedValues, Operator operator);

    protected void validateExpectedValues(List<Object> expectedValues, Operator operator) {
        if (expectedValues == null || expectedValues.isEmpty()) {
            throw new IllegalArgumentException("Expected values cannot be null or empty for operator: " + operator);
        }
        
        if (operator == Operator.BT && expectedValues.size() < 2) {
            throw new IllegalArgumentException("BETWEEN operator requires exactly 2 values, got: " + expectedValues.size());
        }
    }
}