package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.storage.entity.PolicyDefinitionEntity;
import com.jrules.ruleengine.v2.storage.entity.PolicyStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class PolicySummary {

    private String id;
    private String policyId;
    private String version;
    private String name;
    private PolicyType type;
    private PolicyStatus status;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private long versionCount;

    public static PolicySummary from(PolicyDefinitionEntity e) {
        PolicySummary s = new PolicySummary();
        s.id          = e.getId();
        s.policyId    = e.getPolicyId();
        s.version     = e.getVersion();
        s.name        = e.getName();
        s.type        = e.getType();
        s.status      = e.getStatus();
        s.description = e.getDescription();
        s.createdAt   = e.getCreatedAt();
        s.updatedAt   = e.getUpdatedAt();
        s.createdBy   = e.getCreatedBy();
        return s;
    }
}
