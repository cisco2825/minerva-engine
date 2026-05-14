package com.jrules.ruleengine.v2.api;

import com.jrules.ruleengine.v2.evaluator.rule.RuleChainEvaluator;
import com.jrules.ruleengine.v2.evaluator.scorecard.ScorecardEvaluator;
import com.jrules.ruleengine.v2.evaluator.table.DecisionTableEvaluator;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.model.result.ValidationResult;
import com.jrules.ruleengine.v2.validator.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/policy")
@RequiredArgsConstructor
public class PolicyV2Controller {

    private final RequestValidator requestValidator;
    private final RuleChainEvaluator ruleChainEvaluator;
    private final DecisionTableEvaluator decisionTableEvaluator;
    private final ScorecardEvaluator scorecardEvaluator;

    @PostMapping("/validate")
    public ValidationResult validate(@RequestBody ValidationRequest request) {
        return requestValidator.validate(request);
    }

    @PostMapping("/evaluate")
    public EvaluationResult evaluate(@RequestBody EvaluationRequest request) {
        PolicyType type = request.getPolicy().getType();
        if (type == null) throw new EvaluationException("Policy type must be specified");
        return switch (type) {
            case RULE_CHAIN     -> ruleChainEvaluator.evaluate(request);
            case DECISION_TABLE -> decisionTableEvaluator.evaluate(request);
            case SCORECARD      -> scorecardEvaluator.evaluate(request);
        };
    }
}
