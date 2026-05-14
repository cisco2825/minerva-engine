package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.storage.entity.PolicyStatus;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    private PolicyStatus status;
}
