package com.jrules.ruleengine.v2.parser;

import com.jrules.ruleengine.v2.parser.lexer.Token;

public class ParseException extends RuntimeException {

    private final int line;
    private final int column;

    public ParseException(String message, Token token) {
        super(message + " at " + token.getLine() + ":" + token.getColumn());
        this.line = token.getLine();
        this.column = token.getColumn();
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }
}
