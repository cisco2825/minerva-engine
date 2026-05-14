package com.jrules.ruleengine.v2.validator.stage;

import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.validator.ValidationContext;
import org.springframework.stereotype.Component;

@Component
public class Stage4PolicyValidator {

    private static final String STAGE = "STAGE_4_POLICY";

    public void validate(ValidationContext ctx) {
        ValidationRequest req = ctx.getRequest();
        Policy policy = req.getPolicy();
        if (policy == null || policy.getType() == null) return; // Stage 0 already reported

        PolicyType type = policy.getType();
        switch (type) {
            case RULE_CHAIN -> validateRuleChain(policy, ctx);
            case DECISION_TABLE -> validateDecisionTable(policy, req, ctx);
            case SCORECARD -> validateScorecard(policy, ctx);
        }
    }

    private void validateRuleChain(Policy policy, ValidationContext ctx) {
        if (policy.isGraphBased()) {
            // Graph-based validation handled in Stage5
            return;
        }
        if (policy.getRules() == null || policy.getRules().isEmpty()) {
            ctx.error("POLICY.RULE_CHAIN_NO_RULES", STAGE, "policy.rules",
                    "RULE_CHAIN policy must have at least one rule",
                    "Add rules to the 'rules' array");
        }
    }

    private void validateDecisionTable(Policy policy, ValidationRequest req, ValidationContext ctx) {
        if (policy.getTable() == null) {
            ctx.error("POLICY.DT_NO_TABLE", STAGE, "policy.table",
                    "DECISION_TABLE policy must reference a decision table",
                    "Set the 'table' field on the policy");
            return;
        }
        // If the table has a name, it should be present in the tables map
        String tableName = policy.getTable().getName();
        if (!isBlank(tableName) && (req.getTables() == null || !req.getTables().containsKey(tableName))) {
            ctx.warning("POLICY.DT_TABLE_NOT_IN_MAP", STAGE, "policy.table",
                    "Table '" + tableName + "' is not in the request tables map — it will be evaluated inline");
        }
    }

    private void validateScorecard(Policy policy, ValidationContext ctx) {
        if (policy.getScorecard() == null) {
            ctx.error("POLICY.SCORECARD_NULL", STAGE, "policy.scorecard",
                    "SCORECARD policy must have a scorecard defined");
            return;
        }
        if (policy.getScorecard().getVariables() == null || policy.getScorecard().getVariables().isEmpty()) {
            ctx.error("POLICY.SCORECARD_NO_VARIABLES", STAGE, "policy.scorecard.variables",
                    "Scorecard must have at least one variable");
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
