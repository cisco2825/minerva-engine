package com.jrules.ruleengine.v2.model.result;

import com.jrules.ruleengine.v2.model.graph.NodeType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One step in the execution trace of a graph-based policy evaluation.
 * Each step corresponds to a single node that was visited.
 * The terminal OUTCOME / CUSTOM_OUTPUT node is included as the last step
 * with {@code handleTaken = null}.
 */
@Data
@Builder
public class GraphTraceStep {
    private String nodeId;
    private String nodeName;
    private NodeType nodeType;

    /** The output handle that led to the next node, null for the terminal node. */
    private String handleTaken;

    /** Wall-clock time spent evaluating this node, in milliseconds. */
    private long durationMs;

    /**
     * Per-rule / per-condition results produced inside this node.
     * Populated only when {@code traceLevel != MINIMAL}.
     * Empty for START, SOURCE, and OUTCOME nodes.
     */
    private List<RuleResult> details;
}
