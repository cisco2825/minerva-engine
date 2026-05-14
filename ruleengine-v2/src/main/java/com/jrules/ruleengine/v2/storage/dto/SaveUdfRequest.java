package com.jrules.ruleengine.v2.storage.dto;

import com.jrules.ruleengine.v2.model.udf.UDF;
import lombok.Data;

@Data
public class SaveUdfRequest {

    private String version;
    private String createdBy;
    private UDF udf;
}
