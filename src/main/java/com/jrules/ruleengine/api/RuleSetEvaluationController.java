package com.jrules.ruleengine.api;

import com.jrules.ruleengine.model.RuleSetEvaluationResult;
import com.jrules.ruleengine.service.RuleSetEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ruleset")
@RequiredArgsConstructor
public class RuleSetEvaluationController {

    private final RuleSetEvaluator ruleSetEvaluator;

    @PostMapping("/evaluate")
    public RuleSetEvaluationResult evaluate(@RequestBody RuleSetEvaluationRequest request) {
        return ruleSetEvaluator.evaluate(request.getRuleset(), request.getContext());
    }
}
