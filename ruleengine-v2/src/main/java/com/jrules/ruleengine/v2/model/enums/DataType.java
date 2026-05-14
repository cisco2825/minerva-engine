package com.jrules.ruleengine.v2.model.enums;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum DataType {
    NUMBER("number"),
    TEXT("text"),
    DATE("date"),
    BOOLEAN("boolean");

    public final String token;

    private static final Map<String, DataType> TOKEN_MAP =
        Stream.of(values()).collect(Collectors.toMap(t -> t.token.toLowerCase(), Function.identity()));

    DataType(String token) {
        this.token = token;
    }

    public static DataType from(String token) {
        return token != null ? TOKEN_MAP.get(token.toLowerCase()) : null;
    }
}
