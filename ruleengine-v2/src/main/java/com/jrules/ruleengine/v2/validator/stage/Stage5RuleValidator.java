package com.jrules.ruleengine.v2.validator.stage;

import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.graph.NodeType;
import com.jrules.ruleengine.v2.model.graph.PolicyEdge;
import com.jrules.ruleengine.v2.model.graph.PolicyNode;
import com.jrules.ruleengine.v2.model.graph.config.BranchCondition;
import com.jrules.ruleengine.v2.model.graph.config.BranchNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.OutcomeNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.RuleNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.WorkflowNodeConfig;
import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.rule.Rule;
import com.jrules.ruleengine.v2.model.rule.RuleAction;
import com.jrules.ruleengine.v2.validator.ValidationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class Stage5RuleValidator {

    private static final String STAGE = "STAGE_5_RULE";

    private final ObjectMapper objectMapper;

    public void validate(ValidationContext ctx) {
        Policy policy = ctx.getRequest().getPolicy();
        if (policy == null || policy.getType() != PolicyType.RULE_CHAIN) return;

        if (policy.isGraphBased()) {
            validateGraph(policy, ctx);
        } else {
            validateLegacyRuleChain(policy, ctx);
        }
    }

    // ── Graph validation ──────────────────────────────────────────────────────

    private void validateGraph(Policy policy, ValidationContext ctx) {
        List<PolicyNode> nodes = policy.getNodes();
        List<PolicyEdge> edges = policy.getEdges();

        if (nodes == null || nodes.isEmpty()) {
            ctx.error("GRAPH.NO_NODES", STAGE, "policy.nodes", "Policy graph has no nodes");
            return;
        }

        Set<String> nodeIds = new HashSet<>();
        long startCount   = 0;
        long outcomeCount = 0;

        for (PolicyNode node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                ctx.error("GRAPH.NODE_MISSING_ID", STAGE, "policy.nodes", "A node is missing its id");
                continue;
            }
            if (!nodeIds.add(node.getId())) {
                ctx.error("GRAPH.DUPLICATE_NODE_ID", STAGE, "policy.nodes[" + node.getId() + "]",
                        "Duplicate node id: " + node.getId());
            }
            if (node.getType() == null) {
                ctx.error("GRAPH.NODE_MISSING_TYPE", STAGE, "policy.nodes[" + node.getId() + "]",
                        "Node '" + node.getId() + "' has no type");
                continue;
            }
            if (node.getType() == NodeType.START)   startCount++;
            if (node.getType() == NodeType.OUTCOME) outcomeCount++;

            validateNodeConfig(node, ctx);
        }

        if (startCount == 0) {
            ctx.error("GRAPH.NO_START_NODE", STAGE, "policy.nodes", "Policy graph must have a START node");
        }
        if (startCount > 1) {
            ctx.error("GRAPH.MULTIPLE_START_NODES", STAGE, "policy.nodes",
                    "Policy graph must have exactly one START node");
        }
        if (outcomeCount == 0) {
            ctx.error("GRAPH.NO_OUTCOME_NODE", STAGE, "policy.nodes",
                    "Policy graph must have at least one OUTCOME node");
        }

        // Validate edges reference valid node ids
        if (edges != null) {
            Set<String> edgeIds = new HashSet<>();
            for (PolicyEdge edge : edges) {
                if (edge.getId() != null) edgeIds.add(edge.getId());
                if (!nodeIds.contains(edge.getSource())) {
                    ctx.error("GRAPH.EDGE_INVALID_SOURCE", STAGE, "policy.edges",
                            "Edge '" + edge.getId() + "' references unknown source node: " + edge.getSource());
                }
                if (!nodeIds.contains(edge.getTarget())) {
                    ctx.error("GRAPH.EDGE_INVALID_TARGET", STAGE, "policy.edges",
                            "Edge '" + edge.getId() + "' references unknown target node: " + edge.getTarget());
                }
            }
        }
    }

    private void validateNodeConfig(PolicyNode node, ValidationContext ctx) {
        String loc = "policy.nodes[" + node.getId() + "]";
        try {
            switch (node.getType()) {
                case RULE -> {
                    if (node.getConfig() != null) {
                        RuleNodeConfig cfg = objectMapper.convertValue(node.getConfig(), RuleNodeConfig.class);
                        if (cfg.getRules() != null) {
                            for (Rule r : cfg.getRules()) {
                                if (isBlank(r.getName())) {
                                    ctx.error("GRAPH.RULE_MISSING_NAME", STAGE, loc, "A rule in node '" + node.getId() + "' is missing a name");
                                }
                                if (isBlank(r.getExpression())) {
                                    ctx.error("GRAPH.RULE_MISSING_EXPR", STAGE, loc, "Rule '" + r.getName() + "' in node '" + node.getId() + "' is missing an expression");
                                }
                            }
                        }
                    }
                }
                case BRANCH -> {
                    if (node.getConfig() != null) {
                        BranchNodeConfig cfg = objectMapper.convertValue(node.getConfig(), BranchNodeConfig.class);
                        if (cfg.getConditions() != null) {
                            for (BranchCondition c : cfg.getConditions()) {
                                if (isBlank(c.getId())) {
                                    ctx.error("GRAPH.BRANCH_CONDITION_MISSING_ID", STAGE, loc, "A condition in branch node '" + node.getId() + "' is missing an id");
                                }
                                if (isBlank(c.getExpression())) {
                                    ctx.error("GRAPH.BRANCH_CONDITION_MISSING_EXPR", STAGE, loc, "Condition '" + c.getId() + "' in node '" + node.getId() + "' is missing an expression");
                                }
                            }
                        }
                    }
                }
                case WORKFLOW -> {
                    if (node.getConfig() == null) {
                        ctx.error("GRAPH.WORKFLOW_NO_CONFIG", STAGE, loc, "Workflow node '" + node.getId() + "' has no config");
                    } else {
                        WorkflowNodeConfig cfg = objectMapper.convertValue(node.getConfig(), WorkflowNodeConfig.class);
                        if (isBlank(cfg.getPolicyId())) {
                            ctx.error("GRAPH.WORKFLOW_MISSING_POLICY_ID", STAGE, loc, "Workflow node '" + node.getId() + "' must specify a policyId");
                        }
                    }
                }
                case OUTCOME -> {
                    if (node.getConfig() == null) {
                        ctx.error("GRAPH.OUTCOME_NO_CONFIG", STAGE, loc, "Outcome node '" + node.getId() + "' has no config");
                    } else {
                        OutcomeNodeConfig cfg = objectMapper.convertValue(node.getConfig(), OutcomeNodeConfig.class);
                        if (isBlank(cfg.getOutcome())) {
                            ctx.error("GRAPH.OUTCOME_MISSING_VALUE", STAGE, loc, "Outcome node '" + node.getId() + "' must specify an outcome value");
                        }
                    }
                }
                default -> { /* START, SOURCE need no config validation */ }
            }
        } catch (Exception e) {
            ctx.error("GRAPH.NODE_CONFIG_INVALID", STAGE, loc,
                    "Node '" + node.getId() + "' has invalid config: " + e.getMessage());
        }
    }

    // ── Legacy rule chain validation (backward compat) ────────────────────────

    private void validateLegacyRuleChain(Policy policy, ValidationContext ctx) {
        List<Rule> rules = policy.getRules();
        if (rules == null || rules.isEmpty()) return;

        Set<String> names = new HashSet<>();
        boolean hasStop = false;

        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            String loc = "policy.rules[" + i + "]";

            if (isBlank(rule.getName())) {
                ctx.error("RULE.MISSING_NAME", STAGE, loc, "Rule at index " + i + " must have a name");
            } else {
                loc = "policy.rules[" + rule.getName() + "]";
                if (!names.add(rule.getName().toLowerCase())) {
                    ctx.error("RULE.DUPLICATE_NAME", STAGE, loc, "Duplicate rule name: '" + rule.getName() + "'");
                }
            }

            if (rule.getWorkflowRef() != null) {
                if (isBlank(rule.getWorkflowRef().getPolicyId())) {
                    ctx.error("RULE.WORKFLOW_MISSING_POLICY_ID", STAGE, loc,
                            "Workflow step must specify a policyId");
                }
                continue;
            }

            if (isBlank(rule.getExpression())) {
                ctx.error("RULE.MISSING_EXPRESSION", STAGE, loc, "Rule '" + rule.getName() + "' must have an expression");
            }
            if (rule.getOnPass() == null) {
                ctx.error("RULE.MISSING_ON_PASS", STAGE, loc, "Rule '" + rule.getName() + "' must define an onPass action");
            } else if (rule.getOnPass().getType() == RuleAction.ActionType.STOP) {
                if (isBlank(rule.getOnPass().getOutcome())) {
                    ctx.error("RULE.ON_PASS_STOP_MISSING_OUTCOME", STAGE, loc + ".onPass", "STOP action must specify an outcome");
                }
                hasStop = true;
            }
            if (rule.getOnFail() == null) {
                ctx.error("RULE.MISSING_ON_FAIL", STAGE, loc, "Rule '" + rule.getName() + "' must define an onFail action");
            } else if (rule.getOnFail().getType() == RuleAction.ActionType.STOP) {
                if (isBlank(rule.getOnFail().getOutcome())) {
                    ctx.error("RULE.ON_FAIL_STOP_MISSING_OUTCOME", STAGE, loc + ".onFail", "STOP action must specify an outcome");
                }
                hasStop = true;
            }
        }
        if (!hasStop && policy.getDefaultAction() == null) {
            ctx.warning("RULE.NO_DEFAULT_ACTION", STAGE, "policy.defaultAction",
                    "No STOP action and no defaultAction — evaluation will have no outcome");
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
