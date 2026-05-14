package com.jrules.ruleengine.v2.parser.lexer;

import lombok.Data;

@Data
public class Token {
    private final TokenType type;
    private final String value;
    private final int line;
    private final int column;

    public boolean is(TokenType type) {
        return this.type == type;
    }

    @Override
    public String toString() {
        return String.format("[%s '%s' at %d:%d]", type, value, line, column);
    }
}
