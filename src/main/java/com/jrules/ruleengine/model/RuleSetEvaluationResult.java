package com.jrules.ruleengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetEvaluationResult {
    private boolean passed;
    private Map<String, Boolean> criteriaEvaluationResult;
}