package com.jrules.ruleengine.v2.parser.lexer;

public enum TokenType {

    // literals
    NUMBER, STRING, BOOLEAN, NULL,

    // identifiers and paths
    IDENTIFIER,

    // lookup reference
    AT,

    // keywords (case-insensitive)
    AND, OR, NOT,
    IN, BETWEEN, IS,
    CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES,
    TABLE,
    TRUE, FALSE,
    LET,

    // statement separator (used by let blocks)
    SEMICOLON,

    // comparison operators
    EQ, NEQ, LT, LTE, GT, GTE,

    // arithmetic operators
    PLUS, MINUS, STAR, SLASH, PERCENT,

    // ternary
    QUESTION, COLON,

    // delimiters
    LPAREN, RPAREN,
    LBRACKET, RBRACKET,
    COMMA, DOT,

    EOF
}
