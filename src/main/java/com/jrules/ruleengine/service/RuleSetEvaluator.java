package com.jrules.ruleengine.service;

import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.evaluator.formula.FormulaEvaluator;
import com.jrules.ruleengine.evaluator.criteria.factory.CriterionEvaluatorFactory;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import com.jrules.ruleengine.model.RuleSetEvaluationResult;
import com.jrules.ruleengine.utils.ContextResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.jrules.ruleengine.exception.RuleSetValidationException;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.ThrowableProblem;

import java.util.HashMap;
import java.util.Map;

/**
 * Core component for evaluating loan marketplace rules against application data.
 * <p>
 * This evaluator processes rule files containing conditions and expressions to determine
 * loan eligibility. It performs comprehensive validation, condition evaluation, and
 * expression parsing to produce final eligibility results.
 * </p>
 * <p>
 * The evaluation process includes:
 * </p>
 * <ul>
 *   <li>Rule file validation using {@link com.jrules.ruleengine.service.RuleSetValidationService}</li>
 *   <li>Individual condition evaluation against application context</li>
 *   <li>Expression evaluation combining condition results</li>
 *   <li>Comprehensive error handling and logging</li>
 * </ul>
 *
 * @author Shubham Thakur
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSetEvaluator {

    private final RuleSetValidationService rulesetValidationService;
    private final CriterionEvaluatorFactory criterionEvaluatorFactory;
    private final FormulaEvaluator formulaEvaluator;

    /**
     * Evaluates a rule file against the provided application context.
     * <p>
     * Performs complete rule evaluation including validation, condition processing,
     * and expression evaluation. Returns detailed results including failed conditions
     * for debugging and audit purposes.
     * </p>
     *
     * @param ruleSet the rule file containing conditions and expressions to evaluate.
     *                 Must not be null.
     * @param context the application data context for parameter resolution.
     *                Must not be null.
     * @return {@link com.jrules.ruleengine.model.RuleSetEvaluationResult} containing evaluation outcome and failed conditions
     * @throws ThrowableProblem if validation fails, evaluation errors occur, or inputs are invalid
     */
    public RuleSetEvaluationResult evaluate(RuleSet ruleSet, Map<String, ? extends Object> context) {
        validateInputs(ruleSet, context);
        try {
            validateRuleFile(ruleSet);

            log.info("Starting ruleset evaluation with {} conditions", ruleSet.getCriteria().size());

            Map<String, Boolean> conditionResults = evaluateCriteria(ruleSet, context);
            
            boolean finalResult = evaluateFormula(ruleSet.getFormula(), conditionResults);

            log.info("Ruleset evaluation completed with result: {}, condition results: {}",
                     finalResult, conditionResults);
            return new RuleSetEvaluationResult(finalResult, conditionResults);
            
        } catch (RuleSetValidationException e) {
            throw e;
        } catch (ThrowableProblem p) {
            throw p;
        } catch (Exception e) {
            log.error("Unexpected error during rule evaluation: {}", e.getMessage(), e);
            throw Problem.valueOf(Status.INTERNAL_SERVER_ERROR, "Rule evaluation failed: " + e.getMessage());
        }
    }

    /**
     * Validates input parameters for rule evaluation.
     *
     * @param ruleSet the rule file to validate
     * @param context the context to validate
     * @throws ThrowableProblem if inputs are null
     */
    private void validateInputs(RuleSet ruleSet, Map<String, ? extends Object> context) {
        if (ruleSet == null) {
            log.error("RuleFile is null");
            throw Problem.valueOf(Status.BAD_REQUEST, "RuleFile cannot be null");
        }
        
        if (context == null) {
            log.error("Context is null");
            throw Problem.valueOf(Status.BAD_REQUEST, "Context cannot be null");
        }
    }

    /**
     * Validates the rule file structure and content.
     *
     * @param ruleSet the rule file to validate
     * @throws ThrowableProblem if validation fails
     */
    private void validateRuleFile(RuleSet ruleSet) {
        RuleSetValidationResult validationResult = rulesetValidationService.validate(ruleSet);
        
        if (!validationResult.isValid()) {
            log.error("Rule validation failed with {} errors", validationResult.getErrors().size());
            throw new RuleSetValidationException("Rule validation failed", validationResult);
        }
    }

    /**
     * Evaluates all criterion in the rule file against the context.
     *
     * @param ruleSet the rule file containing conditions
     * @param context the application context for parameter resolution
     * @return map of condition names to their evaluation results
     * @throws ThrowableProblem if condition evaluation fails
     */
    private Map<String, Boolean> evaluateCriteria(RuleSet ruleSet, Map<String, ? extends Object> context) {
        Map<String, Boolean> conditionResults = new HashMap<>();

      ruleSet.getCriteria().forEach(condition -> {
        boolean result = evaluateCriterion(condition, context);
        conditionResults.put(condition.getName(), result);
        log.debug("Condition '{}' evaluated to: {}", condition.getName(), result);
      });
        
      return conditionResults;
    }

    /**
     * Evaluates a single condition against the context.
     *
     * @param criterion the condition to evaluate
     * @param context the application context
     * @return true if condition passes, false otherwise
     * @throws ThrowableProblem if evaluation fails
     */
    private boolean evaluateCriterion(RuleSet.Criterion criterion, Map<String, ? extends Object> context) {
        try {
            Object actualValue = ContextResolver.resolve(context, criterion.getParam());
            DataType dataType = DataType.from(criterion.getDatasource().getDatatype());
            
            return criterionEvaluatorFactory.get(dataType).evaluate(criterion, actualValue);
            
        } catch (IllegalArgumentException e) {
            log.error("Error evaluating condition '{}': {}", criterion.getName(), e.getMessage(), e);
            throw Problem.valueOf(Status.BAD_REQUEST,
                                "Condition '" + criterion.getName() + "' evaluation failed: " + e.getMessage());
        }
    }

    /**
     * Evaluates the rule expression using condition results.
     *
     * @param expression the rule expression to evaluate
     * @param conditionResults map of condition results
     * @return true if expression evaluates to true, false otherwise
     * @throws ThrowableProblem if expression evaluation fails
     */
    private boolean evaluateFormula(String expression, Map<String, Boolean> conditionResults) {
        try {
            return formulaEvaluator.evaluate(expression, conditionResults);
        } catch (Exception e) {
            log.error("Error evaluating expression '{}': {}", expression, e.getMessage(), e);
            throw Problem.valueOf(Status.INTERNAL_SERVER_ERROR, "Expression evaluation failed: " + e.getMessage());
        }
    }
}
