package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.lookup.Lookup;
import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.model.udf.UDF;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SavePolicyRequest {

    private String description;
    private String createdBy;

    private Policy policy;
    private List<UDF> udfs;
    private Map<String, DecisionTable> tables;
    private Map<String, Lookup> lookups;
}
