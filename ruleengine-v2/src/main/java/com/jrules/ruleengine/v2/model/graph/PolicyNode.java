package com.jrules.ruleengine.v2.model.graph;

import lombok.Data;

import java.util.Map;

@Data
public class PolicyNode {
    private String id;
    private NodeType type;
    private String name;
    private NodePosition position;
    private Map<String, Object> config;
}
