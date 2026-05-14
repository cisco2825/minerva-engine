// Generated from com/jrules/ruleengine/antlr/RuleSetFormula.g4 by ANTLR 4.13.1
package com.jrules.ruleengine.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link RuleSetFormulaParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface RuleSetFormulaVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link RuleSetFormulaParser#parse}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParse(RuleSetFormulaParser.ParseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RuleSetFormulaParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr(RuleSetFormulaParser.ExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RuleSetFormulaParser#orExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpr(RuleSetFormulaParser.OrExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RuleSetFormulaParser#andExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpr(RuleSetFormulaParser.AndExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RuleSetFormulaParser#notExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotExpr(RuleSetFormulaParser.NotExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RuleSetFormulaParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtom(RuleSetFormulaParser.AtomContext ctx);
}