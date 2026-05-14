package com.jrules.ruleengine.v2.model.result;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class RuleResult {
    private String name;
    private String expression;
    private boolean result;
    private String action;
    private String outcome;
    private Map<String, Object> resolvedValues;
}
