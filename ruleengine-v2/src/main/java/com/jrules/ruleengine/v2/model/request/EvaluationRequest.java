package com.jrules.ruleengine.v2.model.request;

import com.jrules.ruleengine.v2.model.enums.TraceLevel;
import com.jrules.ruleengine.v2.model.lookup.Lookup;
import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.model.udf.UDF;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class EvaluationRequest {

    private Policy policy;
    private List<UDF> udfs;
    private Map<String, DecisionTable> tables;
    private Map<String, Lookup> lookups;
    private Map<String, Object> context;
    private TraceLevel traceLevel = TraceLevel.STANDARD;
    private Set<String> activeChain = new HashSet<>();
}
