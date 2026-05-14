package com.jrules.ruleengine.v2.model.graph;

import lombok.Data;

@Data
public class PolicyEdge {
    private String id;
    private String source;
    private String sourceHandle;
    private String target;
}
