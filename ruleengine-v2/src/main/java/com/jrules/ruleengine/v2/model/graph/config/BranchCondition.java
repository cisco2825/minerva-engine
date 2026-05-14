package com.jrules.ruleengine.v2.model.graph.config;

import lombok.Data;

@Data
public class BranchCondition {
    private String id;
    private String expression;
    private String label;
}
