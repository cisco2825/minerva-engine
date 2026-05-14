package com.jrules.ruleengine.evaluator.criteria.factory;

import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.evaluator.criteria.CriterionEvaluator;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class CriterionEvaluatorRegistry {
    
    private final Map<DataType, CriterionEvaluator> evaluators = new EnumMap<>(DataType.class);
    
    public void register(DataType dataType, CriterionEvaluator evaluator) {
        evaluators.put(dataType, evaluator);
    }
    
    public CriterionEvaluator get(DataType dataType) {
        CriterionEvaluator evaluator = evaluators.get(dataType);
        if (evaluator == null) {
            throw new IllegalArgumentException("No evaluator registered for datatype: " + dataType);
        }
        return evaluator;
    }
}