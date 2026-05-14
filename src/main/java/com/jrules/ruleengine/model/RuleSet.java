package com.jrules.ruleengine.model;

import com.jrules.ruleengine.datasource.DataSourceConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.util.List;

/**
 * Represents a complete rule set for loan marketplace eligibility evaluation.
 * <p>
 * A RuleSet contains a logical formula that combines multiple conditions using
 * boolean operators (AND, OR) and parentheses for grouping. Each condition
 * defines specific criteria that must be evaluated against loan application data.
 * </p>
 * <p>
 * Example structure:
 * </p>
 * <pre>
 * {
 *   "version": "1.0",
 *   "formula": "c1 AND (c2 OR c3)",
 *   "conditions": [
 *     {
 *       "name": "c1",
 *       "param": "metadata.loanAmount",
 *       "datatype": "number",
 *       "operator": "gte",
 *       "value": [50000]
 *     }...
 *   ]
 * }
 * </pre>
 *
 * @author Shubham Thakur
 */
@Builder
@ToString
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RuleSet {

    /**
     * Version identifier for the rule set.
     * Used for tracking rule changes and compatibility.
     */
    private String version;

    /**
     * Boolean expression formula combining condition names.
     * <p>
     * Supports logical operators (AND, OR) and parentheses for grouping.
     * Criterion names in the formula must match the names defined in the conditions list.
     * </p>
     * <p>
     * Example: "c1 AND (c2 OR c3)"
     * </p>
     */
    private String formula;

    /**
     * List of criteria that can be referenced in the formula.
     */
    private List<Criterion> criteria;

    /**
     * Represents a single evaluation criterion within a rule set.
     * <p>
     * A criterion is a independent condition that can be used in the formula expression with
     * logical operators to make a meaningful evaluation criteria
     * </p>
     */
    @Data
    public static class Criterion {

        /**
         * Unique identifier for the criterion within the rule set.
         * <p>
         * This name is referenced in the rule formula to combine conditions
         * using boolean logic. Must be unique within the rule set.
         * </p>
         * <p>
         * Example: "c1", "ageCheck", "incomeValidation"
         * </p>
         */
        private String name;

        /**
         * Name of the parameter on which the criterion is applied. This parameter value should
         * be provided in the context.
         */
        private String param;

        /**
         * Comparison operator for evaluating the condition.
         * <p>
         * Defines how the actual parameter value should be compared against
         * the expected values. Available operators depend on the data type.
         * </p>
         * <p>
         * Common operators:
         * </p>
         * <ul>
         *   <li>"eq" - equals</li>
         *   <li>"neq" - not equals</li>
         *   <li>"gt" - greater than</li>
         *   <li>"gte" - greater than or equal</li>
         *   <li>"lt" - less than</li>
         *   <li>"lte" - less than or equal</li>
         *   <li>"in" - value in list</li>
         *   <li>"bt" - between (requires 2 values)</li>
         * </ul>
         */
        private String operator;

        /**
         * Data source configuration for fetching values.
         * <p>
         * Supports multiple data source types:
         * </p>
         * <ul>
         *   <li>inline - Direct values</li>
         *   <li>s3 - Values stored in S3 bucket</li>
         * </ul>
         */
        private DataSourceConfig datasource;
    }
}