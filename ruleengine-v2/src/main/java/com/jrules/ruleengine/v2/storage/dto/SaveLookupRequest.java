package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.lookup.Lookup;
import lombok.Data;

@Data
public class SaveLookupRequest {

    private String lookupId;
    private String version;
    private String name;
    private String description;
    private String createdBy;
    private Lookup lookup;
}
