package com.jrules.ruleengine.v2.model.rule;

import lombok.Data;

import java.util.Map;

@Data
public class RuleAction {

    public enum ActionType { CONTINUE, STOP }

    private ActionType type;
    private String outcome;
    private String reason;
    private Map<String, Object> outputFields;
}
