package com.jrules.ruleengine.v2.model.rule;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowRef {

    private String policyId;
    private String version;     // null = latest active version
    private String resultKey;   // key injected into context; defaults to policyId

    // Outcome-based routing after the sub-policy runs
    private Map<String, RuleAction> onOutcome;  // outcome string → action
    private RuleAction onDefault;               // fallback when outcome not in onOutcome map
}
