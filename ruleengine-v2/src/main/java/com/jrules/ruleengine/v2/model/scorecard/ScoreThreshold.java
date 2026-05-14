package com.jrules.ruleengine.v2.model.scorecard;

import lombok.Data;

@Data
public class ScoreThreshold {
    private double min;
    private double max;
    private String outcome;
    private String label;
}
