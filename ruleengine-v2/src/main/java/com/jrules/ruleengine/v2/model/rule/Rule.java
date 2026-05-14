package com.jrules.ruleengine.v2.model.rule;

import com.jrules.ruleengine.v2.model.enums.OnMissing;
import lombok.Data;

@Data
public class Rule {

    private String name;
    private String description;
    private String expression;
    private String cantDecideExpression;
    private int priority;
    private RuleAction onPass;
    private RuleAction onFail;
    private OnMissing onMissing = OnMissing.FAIL;

    // If set, this step invokes a sub-policy instead of evaluating an expression
    private WorkflowRef workflowRef;
}
