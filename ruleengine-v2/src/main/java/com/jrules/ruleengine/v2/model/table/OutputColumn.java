package com.jrules.ruleengine.v2.model.table;

import com.jrules.ruleengine.v2.model.enums.DataType;
import lombok.Data;

@Data
public class OutputColumn {
    private String name;
    private DataType datatype;
}
