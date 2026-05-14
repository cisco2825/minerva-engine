package com.jrules.ruleengine.v2.evaluator.expression;

import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;

/**
 * Walks an AST produced by ExpressionParser and returns the evaluated value.
 * Delegates to FunctionRegistry, LookupResolver, and typed comparators.
 */
public interface ExpressionEvaluator {

    Object evaluate(ExpressionNode ast, EvaluationRequest request);
}
