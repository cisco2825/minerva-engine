package com.jrules.ruleengine.evaluator.formula;

import java.util.Map;

public interface FormulaEvaluator {
    boolean evaluate(String expression, Map<String, Boolean> results);
}