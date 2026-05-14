package com.jrules.ruleengine.v2.model.udf;

import com.jrules.ruleengine.v2.model.enums.DataType;
import lombok.Data;

import java.util.List;

@Data
public class UDF {
    private String name;
    private List<UDFParam> params;
    private String expression;
    private DataType returnType;
}
