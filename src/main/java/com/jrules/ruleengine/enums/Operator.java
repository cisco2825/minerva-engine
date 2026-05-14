package com.jrules.ruleengine.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enumeration of comparison operators for rule criterion evaluation.
 * <p>
 * Each operator defines:
 * </p>
 * <ul>
 *   <li>Token string for JSON representation</li>
 *   <li>Expected number of values (-1 for variable count)</li>
 *   <li>Compatible data types</li>
 * </ul>
 *
 * @author Shubham Thakur
 */
public enum Operator {

    /** Equals operator - checks if values are equal */
    EQ("eq", 1, EnumSet.of(DataType.NUMBER, DataType.TEXT, DataType.DATE)),
    
    /** Not equals operator - checks if values are not equal */
    NEQ("neq", 1, EnumSet.of(DataType.NUMBER, DataType.TEXT, DataType.DATE)),
    
    /** Greater than operator - numeric and date comparison */
    GT("gt", 1, EnumSet.of(DataType.NUMBER, DataType.DATE)),
    
    /** Less than operator - numeric and date comparison */
    LT("lt", 1, EnumSet.of(DataType.NUMBER, DataType.DATE)),
    
    /** Greater than or equal operator - numeric and date comparison */
    GTE("gte", 1, EnumSet.of(DataType.NUMBER, DataType.DATE)),
    
    /** Less than or equal operator - numeric and date comparison */
    LTE("lte", 1, EnumSet.of(DataType.NUMBER, DataType.DATE)),
    
    /** Between operator - requires exactly 2 values for range checking */
    BT("bt", 2, EnumSet.of(DataType.NUMBER, DataType.DATE)),
    
    /** In operator - checks if value exists in provided list */
    IN("in", -1, EnumSet.of(DataType.NUMBER, DataType.TEXT)),
    
    /** Not in operator - checks if value does not exist in provided list */
    NOTIN("notIn", -1, EnumSet.of(DataType.NUMBER, DataType.TEXT)),
    
    /** Before operator - date-specific less than comparison */
    BEFORE("before", 1, EnumSet.of(DataType.DATE)),
    
    /** After operator - date-specific greater than comparison */
    AFTER("after", 1, EnumSet.of(DataType.DATE));

    /** String token used in JSON rule definitions */
    public final String token;
    
    /** Expected number of values: positive number for exact count, -1 for variable count */
    public final int expectedCount;
    
    /** Set of data types this operator can be applied to */
    public final Set<DataType> allowedTypes;

    private static final Map<String, Operator> TOKEN_MAP = 
        Stream.of(values()).collect(Collectors.toMap(op -> op.token.toLowerCase(), Function.identity()));

    Operator(String token, int expectedCount, Set<DataType> allowedTypes) {
        this.token = token;
        this.expectedCount = expectedCount;
        this.allowedTypes = EnumSet.copyOf(allowedTypes);
    }

    /**
     * Checks if this operator is valid for the given data type.
     * 
     * @param dataType the data type to check compatibility with
     * @return true if operator supports the data type, false otherwise
     */
    public boolean isValidFor(DataType dataType) {
        return dataType != null && allowedTypes.contains(dataType);
    }

    /**
     * Finds an operator by its token string (case-insensitive).
     * 
     * @param token the operator token to look up
     * @return the matching operator, or null if not found
     */
    public static Operator from(String token) {
        return token != null ? TOKEN_MAP.get(token.toLowerCase()) : null;
    }

    /**
     * Checks if the operator requires a variable number of values.
     * 
     * @return true if operator accepts 1 or more values, false if fixed count
     */
    public boolean isVariableCount() {
        return expectedCount == -1;
    }

    /**
     * Gets the minimum number of values required for this operator.
     * 
     * @return minimum value count (1 for variable count operators)
     */
    public int getMinValueCount() {
        return isVariableCount() ? 1 : expectedCount;
    }
}