package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.enums.TraceLevel;
import com.jrules.ruleengine.v2.storage.entity.EvaluationLogEntity;
import com.jrules.ruleengine.v2.storage.entity.EvaluationStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class EvaluationLogSummary {

    private String id;
    private String policyId;
    private String policyVersion;
    private String policyDefId;
    private EvaluationStatus status;
    private String outcome;
    private String errorMessage;
    private TraceLevel traceLevel;
    private Long durationMs;
    private String evaluatedBy;
    private Instant evaluatedAt;

    public static EvaluationLogSummary from(EvaluationLogEntity e) {
        EvaluationLogSummary s = new EvaluationLogSummary();
        s.setId(e.getId());
        s.setPolicyId(e.getPolicyId());
        s.setPolicyVersion(e.getPolicyVersion());
        s.setPolicyDefId(e.getPolicyDefId());
        s.setStatus(e.getStatus());
        s.setOutcome(e.getOutcome());
        s.setErrorMessage(e.getErrorMessage());
        s.setTraceLevel(e.getTraceLevel());
        s.setDurationMs(e.getDurationMs());
        s.setEvaluatedBy(e.getEvaluatedBy());
        s.setEvaluatedAt(e.getEvaluatedAt());
        return s;
    }
}
