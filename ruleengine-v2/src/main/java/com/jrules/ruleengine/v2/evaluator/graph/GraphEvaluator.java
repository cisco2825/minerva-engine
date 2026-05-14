package com.jrules.ruleengine.v2.evaluator.graph;

import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;

public interface GraphEvaluator {
    EvaluationResult evaluate(EvaluationRequest request);
}
