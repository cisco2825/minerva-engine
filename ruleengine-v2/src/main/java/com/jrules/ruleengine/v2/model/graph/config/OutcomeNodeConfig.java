package com.jrules.ruleengine.v2.model.graph.config;

import lombok.Data;

import java.util.Map;

@Data
public class OutcomeNodeConfig {
    private String outcome;
    private Map<String, Object> outputFields;
    /** Expression strings evaluated at runtime against context; merged with outputFields (wins on conflict). */
    private Map<String, String> outputExpressions;
}
