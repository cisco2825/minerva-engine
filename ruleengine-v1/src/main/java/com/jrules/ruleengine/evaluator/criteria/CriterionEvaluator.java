package com.jrules.ruleengine.evaluator.criteria;

import com.jrules.ruleengine.model.RuleSet;

/**
 * Interface for evaluating criteria in the loan marketplace rule engine.
 * <p>
 * Implementations of this interface are responsible for determining whether
 * a given criterion is satisfied by comparing it against an actual value.
 * This is a core component of the rule evaluation system that enables
 * dynamic loan eligibility and processing decisions.
 * </p>
 *
 * @author Shubham Thakur
 */
public interface CriterionEvaluator {

  /**
   * Evaluates a criterion against an actual value to determine if the criterion is met.
   * <p>
   * This method performs the core logic of criterion evaluation by comparing
   * the provided criterion's criteria against the actual value. The evaluation
   * logic depends on the specific criterion type and operator defined in the criterion.
   * </p>
   *
   * @param criterion the criteria containing operator and expected values for comparison.
   *                  Must not be null.
   * @param actualValue the actual value to evaluate against the criterion.
   *                    Can be null depending on the criterion type.
   * @return {@code true} if the criterion is satisfied by the actual value,
   *         {@code false} otherwise
   * @throws IllegalArgumentException if the criterion value/datatype are incorrect
   */
    boolean evaluate(RuleSet.Criterion criterion, Object actualValue);

}