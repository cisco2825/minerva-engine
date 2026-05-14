package com.jrules.ruleengine.v2.model.udf;

import com.jrules.ruleengine.v2.model.enums.DataType;
import lombok.Data;

@Data
public class UDFParam {
    private String name;
    private DataType type;
}
