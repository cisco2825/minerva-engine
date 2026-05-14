package com.jrules.ruleengine.v2.model.table;

import lombok.Data;

import java.util.List;

@Data
public class TableRow {
    private int priority;
    private List<CellCondition> conditions;
    private Object output;
}
