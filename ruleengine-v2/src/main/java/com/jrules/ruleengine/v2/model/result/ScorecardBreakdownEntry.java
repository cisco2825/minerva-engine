package com.jrules.ruleengine.v2.model.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScorecardBreakdownEntry {
    private String variable;
    private String param;
    private Object value;
    private String matchedBand;
    private double pointsAwarded;
    private String label;
}
