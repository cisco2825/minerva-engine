package com.jrules.ruleengine.v2.model.table;

import com.jrules.ruleengine.v2.model.enums.HitPolicy;
import lombok.Data;

import java.util.List;

@Data
public class DecisionTable {
    private String name;
    private List<InputColumn> inputs;
    private OutputColumn output;
    private HitPolicy hitPolicy;
    private List<TableRow> rows;
}
