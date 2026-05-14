package com.jrules.ruleengine.v2.evaluator.rule;

import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;

/**
 * Evaluates a RULE_CHAIN policy.
 * Iterates rules in priority order, applies onPass/onFail actions,
 * and stops when a STOP action is reached.
 */
public interface RuleChainEvaluator {

    EvaluationResult evaluate(EvaluationRequest request);
}
