package com.jrules.ruleengine.v2.model.scorecard;

import lombok.Data;

import java.util.List;

@Data
public class Scorecard {
    private String name;
    private List<ScorecardVariable> variables;
    private List<ScoreThreshold> thresholds;
    private String defaultOutcome;
}
