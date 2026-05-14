package com.jrules.ruleengine.v2.validator.stage;

import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.validator.ValidationContext;
import org.springframework.stereotype.Component;

@Component
public class Stage0RequestValidator {

    private static final String STAGE = "STAGE_0_REQUEST";

    public void validate(ValidationContext ctx) {
        ValidationRequest req = ctx.getRequest();

        if (req == null) {
            ctx.error("REQ.NULL", STAGE, "request", "Request body is null");
            return;
        }
        if (req.getPolicy() == null) {
            ctx.error("REQ.POLICY_NULL", STAGE, "policy", "Policy is required");
            return;
        }
        if (req.getPolicy().getType() == null) {
            ctx.error("REQ.POLICY_TYPE_NULL", STAGE, "policy.type",
                    "Policy type is required",
                    "Set type to one of: RULE_CHAIN, DECISION_TABLE, SCORECARD");
        }
        if (isBlank(req.getPolicy().getId())) {
            ctx.error("REQ.POLICY_ID_BLANK", STAGE, "policy.id", "Policy id must not be blank");
        }
        if (isBlank(req.getPolicy().getName())) {
            ctx.warning("REQ.POLICY_NAME_BLANK", STAGE, "policy.name",
                    "Policy name is blank — consider adding a descriptive name");
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
