package com.jrules.ruleengine.v2.model.graph.config;

import lombok.Data;

import java.util.Map;

@Data
public class ModelEntry {
    private String name;
    private String type;                        // SCORECARD | DECISION_TABLE | EXPRESSION
    private String resultKey;                   // context key to inject result under (defaults to name)
    private String expression;                  // for EXPRESSION type
    private Map<String, Object> inlineDefinition; // for DECISION_TABLE / SCORECARD
    private int priority;
}
