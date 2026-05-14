package com.jrules.ruleengine.v2.model.graph.config;

import lombok.Data;

@Data
public class WorkflowNodeConfig {
    private String policyId;
    private String version;
    private String resultKey;
}
