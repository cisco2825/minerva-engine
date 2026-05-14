package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.table.DecisionTable;
import lombok.Data;

@Data
public class SaveTableRequest {

    private String tableId;
    private String version;
    private String createdBy;
    private DecisionTable table;
}
