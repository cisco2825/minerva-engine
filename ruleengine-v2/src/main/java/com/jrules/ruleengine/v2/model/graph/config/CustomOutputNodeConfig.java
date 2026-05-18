package com.jrules.ruleengine.v2.model.graph.config;

import lombok.Data;

@Data
public class CustomOutputNodeConfig {
    /** JSON-like template string. Quoted values are literals; unquoted values are expressions. */
    private String template;
}
