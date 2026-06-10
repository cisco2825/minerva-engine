package com.jrules.ruleengine.v2.model.result;

import com.jrules.ruleengine.v2.model.enums.PolicyType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class EvaluationResult {

    private String policyId;
    private String policyVersion;
    private PolicyType policyType;
    private String outcome;
    private Map<String, Object> outputFields;
    /** Populated by CUSTOM_OUTPUT nodes. Contains the evaluated template (List or Map). */
    private Object customOutput;

    // RULE_CHAIN
    private String triggeredBy;
    private List<RuleResult> ruleResults;
    private List<String> skippedRules;
    private List<String> notEvaluated;

    // DECISION_TABLE
    private Object tableOutput;
    private String outputColumn;
    private Map<String, Object> tableInputs;
    private Integer matchedRowPriority;

    // SCORECARD
    private String label;
    private Double totalScore;
    private Double maxPossibleScore;
    private List<ScorecardBreakdownEntry> breakdown;
    private List<String> skippedVariables;

    private long evaluationMs;

    /**
     * Step-by-step execution trace of a graph-based RULE_CHAIN policy.
     * Null for non-graph policies and when traceLevel is MINIMAL.
     * Each entry is one node that was visited, in traversal order.
     * The last entry is the terminal OUTCOME / CUSTOM_OUTPUT node.
     */
    private List<GraphTraceStep> graphTrace;
}
