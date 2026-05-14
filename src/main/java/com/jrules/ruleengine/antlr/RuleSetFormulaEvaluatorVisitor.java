package com.jrules.ruleengine.antlr;

import java.util.Map;

/**
 * Evaluates a parsed boolean rule expression using a map of criterion-name -> boolean value.
 * Example:
 *   Expression: (c1 AND c2) OR c3
 *   Map: { c1=true, c2=false, c3=true }
 *   Result: true
 *
 * @author Shubham Thakur
 */
public class RuleSetFormulaEvaluatorVisitor extends RuleSetFormulaBaseVisitor<Boolean> {

  private final Map<String, Boolean> results;

  public RuleSetFormulaEvaluatorVisitor(Map<String, Boolean> results) {
    this.results = results;
  }

  @Override
  public Boolean visitExpr(RuleSetFormulaParser.ExprContext ctx) {
    return visit(ctx.orExpr());
  }

  @Override
  public Boolean visitOrExpr(RuleSetFormulaParser.OrExprContext ctx) {
    // First value
    boolean value = visit(ctx.andExpr(0));

    // Apply OR with remaining andExpr nodes
    for (int i = 1; i < ctx.andExpr().size(); i++) {
      value = value || visit(ctx.andExpr(i));
    }
    return value;
  }

  @Override
  public Boolean visitAndExpr(RuleSetFormulaParser.AndExprContext ctx) {
    boolean value = visit(ctx.notExpr(0));
    for (int i = 1; i < ctx.notExpr().size(); i++) {
      value = value && visit(ctx.notExpr(i));
    }
    return value;
  }

  @Override
  public Boolean visitNotExpr(RuleSetFormulaParser.NotExprContext ctx) {
    if (ctx.NOT() != null) {
      return !visit(ctx.notExpr());
    }
    return visit(ctx.atom());
  }


  @Override
  public Boolean visitAtom(RuleSetFormulaParser.AtomContext ctx) {
    if (ctx.IDENTIFIER() != null) {
      String name = ctx.IDENTIFIER().getText();
      Boolean val = results.get(name);
      if (val == null)
        throw new IllegalArgumentException("No boolean result found for identifier: " + name);
      return val;
    }
    // Parenthesized expression
    return visit(ctx.expr());
  }
}