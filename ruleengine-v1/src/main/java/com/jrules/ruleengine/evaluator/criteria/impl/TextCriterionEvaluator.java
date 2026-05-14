package com.jrules.ruleengine.evaluator.criteria.impl;

import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.evaluator.criteria.AbstractCriterionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates text criteria against actual values using string comparison operators.
 * 
 * <p>This evaluator handles text comparisons including equality, inequality,
 * and membership tests. It supports the following operators:
 * <ul>
 *   <li>EQ - equals (case-insensitive)</li>
 *   <li>NEQ - not equals (case-insensitive)</li>
 *   <li>IN - value exists in list (case-insensitive)</li>
 *   <li>NOTIN - value does not exist in list (case-insensitive)</li>
 * </ul>
 * 
 * <p>All string comparisons are performed case-insensitively using
 * {@link String#equalsIgnoreCase(String)} for consistency.
 *
 * @author Shubham Thakur
 */
@Slf4j
@Service("text-criterion-evaluator")
public class TextCriterionEvaluator extends AbstractCriterionEvaluator<String> {

    @Override
    protected String parseValue(Object value) {
        return value.toString();
    }
    
    @Override
    protected void validateExpectedValues(List<Object> expectedValues, Operator operator) {
        super.validateExpectedValues(expectedValues, operator);
        
        // Check for null values in the expected values list
        if (expectedValues.stream().anyMatch(Objects::isNull)) {
            log.warn("Expected values contain null entries for operator: {}", operator);
        }
    }
    
    @Override
    protected boolean evaluateByOperator(String actual, List<Object> expectedValues, Operator operator) {
        return switch (operator) {
            case EQ -> evaluateEquals(actual, expectedValues.get(0));
            case NEQ -> evaluateNotEquals(actual, expectedValues.get(0));
            case IN -> evaluateIn(actual, expectedValues);
            case NOTIN -> evaluateNotIn(actual, expectedValues);
            default -> throw new IllegalArgumentException("Unsupported operator for text: " + operator);
        };
    }
    
    /**
     * Evaluates string equality using case-insensitive comparison.
     * 
     * @param actual the actual string value
     * @param expected the expected value
     * @return true if strings are equal (case-insensitive)
     */
    private boolean evaluateEquals(String actual, Object expected) {
        if (expected == null) {
            return false;
        }
        return actual.equalsIgnoreCase(expected.toString());
    }
    
    /**
     * Evaluates string inequality using case-insensitive comparison.
     * 
     * @param actual the actual string value
     * @param expected the expected value
     * @return true if strings are not equal (case-insensitive)
     */
    private boolean evaluateNotEquals(String actual, Object expected) {
        return !evaluateEquals(actual, expected);
    }
    
    /**
     * Evaluates if the actual value exists in the list of expected values.
     * 
     * @param actual the actual string value
     * @param expectedValues the list of expected values
     * @return true if actual value is found in the list (case-insensitive)
     */
    private boolean evaluateIn(String actual, List<Object> expectedValues) {
        return expectedValues.stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> actual.equalsIgnoreCase(value.toString()));
    }

    /**
     * Evaluates if the actual value does not exist in the list of expected values.
     * 
     * @param actual the actual string value
     * @param expectedValues the list of expected values
     * @return true if actual value is not found in the list (case-insensitive)
     */
    private boolean evaluateNotIn(String actual, List<Object> expectedValues) {
        return expectedValues.stream()
                .filter(Objects::nonNull)
                .noneMatch(value -> actual.equalsIgnoreCase(value.toString()));
    }
}
