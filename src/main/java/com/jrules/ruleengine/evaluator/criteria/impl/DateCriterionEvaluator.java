package com.jrules.ruleengine.evaluator.criteria.impl;

import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.evaluator.criteria.AbstractCriterionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Evaluates date criteria against actual values using various comparison operators.
 * 
 * <p>This evaluator handles date comparisons including equality, inequality, range checks,
 * and temporal operations. It supports the following operators:
 * <ul>
 *   <li>EQ - equals</li>
 *   <li>NEQ - not equals</li>
 *   <li>GT - greater than (after)</li>
 *   <li>LT - less than (before)</li>
 *   <li>GTE - greater than or equal (on or after)</li>
 *   <li>LTE - less than or equal (on or before)</li>
 *   <li>BT - between (inclusive range)</li>
 *   <li>BEFORE - before specified date</li>
 *   <li>AFTER - after specified date</li>
 * </ul>
 * 
 * <p>All date values are parsed using ISO date format (yyyy-MM-dd) and converted
 * to LocalDate for comparison.
 *
 * @author Shubham Thakur
 */
@Slf4j
@Service("date-criterion-evaluator")
public class DateCriterionEvaluator extends AbstractCriterionEvaluator<LocalDate> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    protected LocalDate parseValue(Object value) {
        return LocalDate.parse(value.toString(), DATE_FORMATTER);
    }
    
    @Override
    protected boolean evaluateByOperator(LocalDate actual, List<Object> expectedValues, Operator operator) {
        return switch (operator) {
            case EQ -> evaluateEquals(actual, expectedValues.get(0));
            case NEQ -> evaluateNotEquals(actual, expectedValues.get(0));
            case GT, AFTER -> evaluateAfter(actual, expectedValues.get(0));
            case LT, BEFORE -> evaluateBefore(actual, expectedValues.get(0));
            case GTE -> evaluateOnOrAfter(actual, expectedValues.get(0));
            case LTE -> evaluateOnOrBefore(actual, expectedValues.get(0));
            case BT -> evaluateBetween(actual, expectedValues.get(0), expectedValues.get(1));
            default -> throw new IllegalArgumentException("Unsupported operator for date: " + operator);
        };
    }
    
    private boolean evaluateEquals(LocalDate actual, Object expected) {
        return actual.equals(parseValue(expected));
    }
    
    private boolean evaluateNotEquals(LocalDate actual, Object expected) {
        return !actual.equals(parseValue(expected));
    }
    
    private boolean evaluateAfter(LocalDate actual, Object expected) {
        return actual.isAfter(parseValue(expected));
    }
    
    private boolean evaluateBefore(LocalDate actual, Object expected) {
        return actual.isBefore(parseValue(expected));
    }
    
    private boolean evaluateOnOrAfter(LocalDate actual, Object expected) {
        LocalDate expectedDate = parseValue(expected);
        return actual.equals(expectedDate) || actual.isAfter(expectedDate);
    }
    
    private boolean evaluateOnOrBefore(LocalDate actual, Object expected) {
        LocalDate expectedDate = parseValue(expected);
        return actual.equals(expectedDate) || actual.isBefore(expectedDate);
    }
    
    /**
     * Evaluates if the actual date falls within the specified range (inclusive).
     * 
     * @param actual the actual date
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return true if actual is between start and end dates (inclusive)
     */
    private boolean evaluateBetween(LocalDate actual, Object startDate, Object endDate) {
        LocalDate start = parseValue(startDate);
        LocalDate end = parseValue(endDate);
        return (actual.equals(start) || actual.isAfter(start)) && 
               (actual.equals(end) || actual.isBefore(end));
    }
}