package com.jrules.ruleengine.api;

import com.jrules.ruleengine.model.RuleSet;
import lombok.Data;

import java.util.Map;

@Data
public class RuleSetEvaluationRequest {
    private RuleSet ruleset;
    private Map<String, Object> context;
}
