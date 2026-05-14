package com.jrules.ruleengine.v2.model.graph.config;

import lombok.Data;

import java.util.List;

@Data
public class BranchNodeConfig {
    private List<BranchCondition> conditions;
}
