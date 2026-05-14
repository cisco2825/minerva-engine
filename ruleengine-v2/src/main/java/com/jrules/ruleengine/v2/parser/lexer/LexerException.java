package com.jrules.ruleengine.v2.parser.lexer;

public class LexerException extends RuntimeException {

    private final int line;
    private final int column;

    public LexerException(String message, int line, int column) {
        super(message + " at " + line + ":" + column);
        this.line = line;
        this.column = column;
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }
}
