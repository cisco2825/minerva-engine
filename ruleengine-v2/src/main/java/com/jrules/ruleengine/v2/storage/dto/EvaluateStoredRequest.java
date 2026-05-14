package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.enums.TraceLevel;
import lombok.Data;

import java.util.Map;

@Data
public class EvaluateStoredRequest {

    private Map<String, Object> context;
    private TraceLevel traceLevel = TraceLevel.STANDARD;
    private String evaluatedBy;
}
