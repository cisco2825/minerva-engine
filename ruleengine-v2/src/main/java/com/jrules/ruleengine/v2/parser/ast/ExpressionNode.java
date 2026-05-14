package com.jrules.ruleengine.v2.parser.ast;

/**
 * Base type for all AST nodes produced by the ExpressionParser.
 * Every node carries its source position for error reporting.
 */
public abstract class ExpressionNode {
    public int line;
    public int column;
    public int length;
}
