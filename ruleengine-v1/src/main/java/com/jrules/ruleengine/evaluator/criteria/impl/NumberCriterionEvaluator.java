package com.jrules.ruleengine.evaluator.criteria.impl;

import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.evaluator.criteria.AbstractCriterionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Evaluates numerical criteria against actual values using various comparison operators.
 * 
 * <p>This evaluator handles numeric comparisons including equality, inequality, range checks,
 * and membership tests. It supports the following operators:
 * <ul>
 *   <li>EQ - equals</li>
 *   <li>NEQ - not equals</li>
 *   <li>GT - greater than</li>
 *   <li>LT - less than</li>
 *   <li>GTE - greater than or equal</li>
 *   <li>LTE - less than or equal</li>
 *   <li>BT - between (inclusive range)</li>
 *   <li>IN - value exists in list</li>
 *   <li>NOTIN - value does not exist in list</li>
 * </ul>
 * 
 * <p>All values are converted to double precision for comparison to handle
 * both integer and floating-point numbers consistently.
 * 
 * @author Shubham Thakur
 */
@Slf4j
@Service("number-criterion-evaluator")
public class NumberCriterionEvaluator extends AbstractCriterionEvaluator<Double> {

    @Override
    protected Double parseValue(Object value) {
        return Double.parseDouble(value.toString());
    }
    
    @Override
    protected boolean evaluateByOperator(Double actual, List<Object> expectedValues, Operator operator) {
        return switch (operator) {
            case EQ -> evaluateEquals(actual, expectedValues.get(0));
            case NEQ -> evaluateNotEquals(actual, expectedValues.get(0));
            case GT -> evaluateGreaterThan(actual, expectedValues.get(0));
            case LT -> evaluateLessThan(actual, expectedValues.get(0));
            case GTE -> evaluateGreaterThanOrEqual(actual, expectedValues.get(0));
            case LTE -> evaluateLessThanOrEqual(actual, expectedValues.get(0));
            case BT -> evaluateBetween(actual, expectedValues.get(0), expectedValues.get(1));
            case IN -> evaluateIn(actual, expectedValues);
            case NOTIN -> evaluateNotIn(actual, expectedValues);
            default -> throw new IllegalArgumentException("Unsupported operator for number: " + operator);
        };
    }
    
    private boolean evaluateEquals(Double actual, Object expected) {
        return Double.compare(actual, parseValue(expected)) == 0;
    }
    
    private boolean evaluateNotEquals(Double actual, Object expected) {
        return Double.compare(actual, parseValue(expected)) != 0;
    }
    
    private boolean evaluateGreaterThan(Double actual, Object expected) {
        return actual > parseValue(expected);
    }
    
    private boolean evaluateLessThan(Double actual, Object expected) {
        return actual < parseValue(expected);
    }
    
    private boolean evaluateGreaterThanOrEqual(Double actual, Object expected) {
        return actual >= parseValue(expected);
    }
    
    private boolean evaluateLessThanOrEqual(Double actual, Object expected) {
        return actual <= parseValue(expected);
    }
    
    /**
     * Evaluates if the actual value falls within the specified range (inclusive).
     * 
     * @param actual the actual value
     * @param minValue the minimum value (inclusive)
     * @param maxValue the maximum value (inclusive)
     * @return true if actual is between min and max (inclusive)
     */
    private boolean evaluateBetween(Double actual, Object minValue, Object maxValue) {
        Double min = parseValue(minValue);
        Double max = parseValue(maxValue);
        return actual >= min && actual <= max;
    }
    
    /**
     * Evaluates if the actual value exists in the list of expected values.
     * 
     * @param actual the actual value
     * @param expectedValues the list of expected values
     * @return true if actual value is found in the list
     */
    private boolean evaluateIn(Double actual, List<Object> expectedValues) {
        return expectedValues.stream()
                .mapToDouble(this::parseValue)
                .anyMatch(expected -> Double.compare(actual, expected) == 0);
    }
    
    /**
     * Evaluates if the actual value does not exist in the list of expected values.
     * 
     * @param actual the actual value
     * @param expectedValues the list of expected values
     * @return true if actual value is not found in the list
     */
    private boolean evaluateNotIn(Double actual, List<Object> expectedValues) {
        return expectedValues.stream()
                .mapToDouble(this::parseValue)
                .noneMatch(expected -> Double.compare(actual, expected) == 0);
    }
}
