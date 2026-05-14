package com.jrules.ruleengine.v2.model.table;

import com.jrules.ruleengine.v2.model.enums.Operator;
import lombok.Data;

import java.util.List;

@Data
public class CellCondition {

    public enum CellType { ANY, CONDITION }

    private CellType type;
    private Operator operator;

    // single-value operators (eq, neq, gt, gte, lt, lte)
    private Object value;

    // multi-value operators (bt: [min, max], in: [a, b, c])
    private List<Object> values;
}
