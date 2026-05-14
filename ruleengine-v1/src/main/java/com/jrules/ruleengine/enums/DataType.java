package com.jrules.ruleengine.enums;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enumeration of supported data types for rule criterion evaluation.
 * <p>
 * Defines the data types that can be used in rule criteria, determining
 * how values are parsed, compared, and validated during rule evaluation.
 * </p>
 *
 * @author Shubham Thakur
 */
public enum DataType {
    
    /** Numeric data type for integer and decimal values */
    NUMBER("number"),
    
    /** Text data type for string values and text comparison */
    TEXT("text"),
    
    /** Date data type for temporal values and date comparison */
    DATE("date");

    /** String token used in JSON rule definitions */
    public final String token;

    private static final Map<String, DataType> TOKEN_MAP = 
        Stream.of(values()).collect(Collectors.toMap(dt -> dt.token, Function.identity()));

    DataType(String token) {
        this.token = token;
    }

    /**
     * Finds a data type by its token string (case-insensitive).
     * 
     * @param token the data type token to look up
     * @return the matching data type, or null if not found
     */
    public static DataType from(String token) {
        return token != null ? TOKEN_MAP.get(token.toLowerCase()) : null;
    }

    /**
     * Returns the token string for this data type.
     * 
     * @return lowercase token string
     */
    @Override
    public String toString() {
        return token;
    }
}