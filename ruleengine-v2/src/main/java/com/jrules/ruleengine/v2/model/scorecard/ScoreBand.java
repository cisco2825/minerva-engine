package com.jrules.ruleengine.v2.model.scorecard;

import com.jrules.ruleengine.v2.model.enums.Operator;
import lombok.Data;

import java.util.List;

@Data
public class ScoreBand {
    private Operator operator;
    private Object value;
    private List<Object> values;
    private double points;
    private String label;
}
