package com.jrules.ruleengine.v2.evaluator.scorecard;

import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;

/**
 * Evaluates a SCORECARD policy.
 * Matches each variable's value against its bands,
 * sums points, and maps total to a threshold outcome.
 */
public interface ScorecardEvaluator {

    EvaluationResult evaluate(EvaluationRequest request);
}
