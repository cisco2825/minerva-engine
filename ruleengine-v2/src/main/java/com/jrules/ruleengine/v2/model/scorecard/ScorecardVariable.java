package com.jrules.ruleengine.v2.model.scorecard;

import com.jrules.ruleengine.v2.model.enums.OnMissing;
import lombok.Data;

import java.util.List;

@Data
public class ScorecardVariable {
    private String name;
    private String param;
    private List<ScoreBand> bands;
    private double defaultPoints = 0;
    private OnMissing onMissing = OnMissing.SKIP;
}
