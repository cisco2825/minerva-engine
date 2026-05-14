package com.jrules.ruleengine.v2.model.policy;

import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.graph.PolicyEdge;
import com.jrules.ruleengine.v2.model.graph.PolicyNode;
import com.jrules.ruleengine.v2.model.rule.Rule;
import com.jrules.ruleengine.v2.model.rule.RuleAction;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.model.scorecard.Scorecard;
import lombok.Data;

import java.util.List;

@Data
public class Policy {

    private String id;
    private String name;
    private String version;
    private PolicyType type;

    // Graph-based RULE_CHAIN fields (new model)
    private List<PolicyNode> nodes;
    private List<PolicyEdge> edges;

    // Legacy RULE_CHAIN fields (kept for backward compatibility)
    private List<Rule> rules;
    private RuleAction defaultAction;

    // DECISION_TABLE fields
    private DecisionTable table;

    // SCORECARD fields
    private Scorecard scorecard;

    public boolean isGraphBased() {
        return nodes != null && !nodes.isEmpty();
    }
}
