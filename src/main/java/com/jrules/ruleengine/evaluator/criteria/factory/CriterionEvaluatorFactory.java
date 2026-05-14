package com.jrules.ruleengine.evaluator.criteria.factory;

import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.evaluator.criteria.CriterionEvaluator;
import com.jrules.ruleengine.evaluator.criteria.impl.DateCriterionEvaluator;
import com.jrules.ruleengine.evaluator.criteria.impl.NumberCriterionEvaluator;
import com.jrules.ruleengine.evaluator.criteria.impl.TextCriterionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Factory for retrieving criterion evaluators based on data type.
 * Uses registry pattern for better extensibility and follows Open/Closed Principle.
 *
 * @author Shubham Thakur
 */
@Component
@RequiredArgsConstructor
public class CriterionEvaluatorFactory {

    private final CriterionEvaluatorRegistry registry;
    private final NumberCriterionEvaluator numberEvaluator;
    private final TextCriterionEvaluator textEvaluator;
    private final DateCriterionEvaluator dateEvaluator;

    /**
     * Initializes the evaluator registry with Spring-managed beans.
     */
    @PostConstruct
    private void initializeEvaluators() {
        registry.register(DataType.NUMBER, numberEvaluator);
        registry.register(DataType.TEXT, textEvaluator);
        registry.register(DataType.DATE, dateEvaluator);
    }

    /**
     * Returns the appropriate criterion evaluator for the given data type.
     * 
     * @param dataType the data type to get evaluator for
     * @return criterion evaluator for the specified data type
     * @throws IllegalArgumentException if no evaluator is registered for the data type
     */
    public CriterionEvaluator get(DataType dataType) {
        return registry.get(dataType);
    }
}
