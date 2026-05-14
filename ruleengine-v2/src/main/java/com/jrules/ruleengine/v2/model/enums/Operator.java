package com.jrules.ruleengine.v2.model.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Operator {

    EQ("eq",           1,  EnumSet.of(DataType.NUMBER, DataType.TEXT, DataType.DATE, DataType.BOOLEAN)),
    NEQ("neq",         1,  EnumSet.of(DataType.NUMBER, DataType.TEXT, DataType.DATE, DataType.BOOLEAN)),
    GT("gt",           1,  EnumSet.of(DataType.NUMBER, DataType.DATE)),
    GTE("gte",         1,  EnumSet.of(DataType.NUMBER, DataType.DATE)),
    LT("lt",           1,  EnumSet.of(DataType.NUMBER, DataType.DATE)),
    LTE("lte",         1,  EnumSet.of(DataType.NUMBER, DataType.DATE)),
    BT("bt",           2,  EnumSet.of(DataType.NUMBER, DataType.DATE)),
    IN("in",          -1,  EnumSet.of(DataType.NUMBER, DataType.TEXT)),
    NOT_IN("notIn",   -1,  EnumSet.of(DataType.NUMBER, DataType.TEXT)),
    BEFORE("before",   1,  EnumSet.of(DataType.DATE)),
    AFTER("after",     1,  EnumSet.of(DataType.DATE)),
    CONTAINS("contains",       1, EnumSet.of(DataType.TEXT)),
    STARTS_WITH("startsWith",  1, EnumSet.of(DataType.TEXT)),
    ENDS_WITH("endsWith",      1, EnumSet.of(DataType.TEXT)),
    MATCHES("matches",         1, EnumSet.of(DataType.TEXT));

    public final String token;
    public final int expectedCount;
    public final Set<DataType> allowedTypes;

    private static final Map<String, Operator> TOKEN_MAP =
        Stream.of(values()).collect(Collectors.toMap(op -> op.token.toLowerCase(), Function.identity()));

    Operator(String token, int expectedCount, Set<DataType> allowedTypes) {
        this.token = token;
        this.expectedCount = expectedCount;
        this.allowedTypes = EnumSet.copyOf(allowedTypes);
    }

    public boolean isValidFor(DataType dataType) {
        return dataType != null && allowedTypes.contains(dataType);
    }

    public static Operator from(String token) {
        return token != null ? TOKEN_MAP.get(token.toLowerCase()) : null;
    }

    public boolean isVariableCount() {
        return expectedCount == -1;
    }

    public int getMinValueCount() {
        return isVariableCount() ? 1 : expectedCount;
    }
}
