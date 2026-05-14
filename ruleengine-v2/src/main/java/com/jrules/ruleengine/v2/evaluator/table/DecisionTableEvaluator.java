package com.jrules.ruleengine.v2.evaluator.table;

import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.model.table.DecisionTable;

import java.util.List;

/**
 * Evaluates a DecisionTable against resolved input values.
 * Used both as a top-level policy evaluator (DECISION_TABLE type)
 * and as the implementation behind the TABLE() built-in function.
 */
public interface DecisionTableEvaluator {

    // top-level evaluation — full request
    EvaluationResult evaluate(EvaluationRequest request);

    // called by TABLE() built-in — returns the raw output value
    Object evaluateInline(DecisionTable table, List<Object> inputValues);
}
