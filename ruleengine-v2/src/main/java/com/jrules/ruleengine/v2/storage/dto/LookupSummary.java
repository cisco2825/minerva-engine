package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.lookup.LookupType;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import lombok.Data;

import java.time.Instant;

@Data
public class LookupSummary {

    private String id;
    private String lookupId;
    private String version;
    private String name;
    private String description;
    private LookupType type;
    private AssetStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    public static LookupSummary from(LookupDefinitionEntity e) {
        LookupSummary s = new LookupSummary();
        s.id          = e.getId();
        s.lookupId    = e.getLookupId();
        s.version     = e.getVersion();
        s.name        = e.getName();
        s.description = e.getDescription();
        s.type        = e.getType();
        s.status      = e.getStatus();
        s.createdAt   = e.getCreatedAt();
        s.updatedAt   = e.getUpdatedAt();
        s.createdBy   = e.getCreatedBy();
        return s;
    }
}
