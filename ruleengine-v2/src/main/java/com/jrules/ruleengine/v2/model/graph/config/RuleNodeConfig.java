package com.jrules.ruleengine.v2.model.graph.config;

import com.jrules.ruleengine.v2.model.rule.Rule;
import lombok.Data;

import java.util.List;

@Data
public class RuleNodeConfig {
    private List<Rule> rules;
}
