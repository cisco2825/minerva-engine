package com.jrules.ruleengine.v2.evaluator.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.v2.evaluator.expression.ExpressionEvaluator;
import com.jrules.ruleengine.v2.evaluator.scorecard.ScorecardEvaluator;
import com.jrules.ruleengine.v2.evaluator.table.DecisionTableEvaluator;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.MissingValueException;
import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.enums.TraceLevel;
import com.jrules.ruleengine.v2.model.graph.NodeType;
import com.jrules.ruleengine.v2.model.graph.PolicyEdge;
import com.jrules.ruleengine.v2.model.graph.PolicyNode;
import com.jrules.ruleengine.v2.model.graph.config.BranchCondition;
import com.jrules.ruleengine.v2.model.graph.config.BranchNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.ModelEntry;
import com.jrules.ruleengine.v2.model.graph.config.ModelNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.CustomOutputNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.OutcomeNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.RuleNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.SourceNodeConfig;
import com.jrules.ruleengine.v2.model.graph.config.WorkflowNodeConfig;
import com.jrules.ruleengine.v2.model.lookup.Lookup;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import com.jrules.ruleengine.v2.storage.service.LookupStorageService;
import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.model.result.RuleResult;
import com.jrules.ruleengine.v2.model.rule.Rule;
import com.jrules.ruleengine.v2.model.scorecard.Scorecard;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.parser.ExpressionParser;
import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;
import com.jrules.ruleengine.v2.storage.dto.EvaluateStoredRequest;
import com.jrules.ruleengine.v2.storage.service.PolicyStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultGraphEvaluator implements GraphEvaluator {

    private final ExpressionEvaluator expressionEvaluator;
    private final ExpressionParser expressionParser;
    private final ObjectMapper objectMapper;
    private final DecisionTableEvaluator decisionTableEvaluator;
    private final ScorecardEvaluator scorecardEvaluator;
    private final LookupStorageService lookupStorageService;
    private final CustomOutputTemplateEvaluator customOutputTemplateEvaluator;

    @Lazy
    @Autowired
    private PolicyStorageService policyStorageService;

    @Override
    public EvaluationResult evaluate(EvaluationRequest request) {
        long start = System.currentTimeMillis();
        Policy policy = request.getPolicy();

        Map<String, PolicyNode> nodeMap = policy.getNodes().stream()
                .collect(Collectors.toMap(PolicyNode::getId, n -> n));
        Map<String, Map<String, String>> edgeMap = buildEdgeMap(policy.getEdges());

        PolicyNode current = policy.getNodes().stream()
                .filter(n -> n.getType() == NodeType.START)
                .findFirst()
                .orElseThrow(() -> new EvaluationException("Policy has no START node"));

        // Use a mutable context that enriched nodes (SOURCE, WORKFLOW) can write into
        Map<String, Object> ctx = request.getContext() != null
                ? new HashMap<>(request.getContext()) : new HashMap<>();
        request.setContext(ctx);

        List<RuleResult> trace = new ArrayList<>();
        TraceLevel traceLevel = request.getTraceLevel() != null
                ? request.getTraceLevel() : TraceLevel.STANDARD;

        int maxSteps = 200;
        while (current.getType() != NodeType.OUTCOME && current.getType() != NodeType.CUSTOM_OUTPUT) {
            if (--maxSteps <= 0) {
                throw new EvaluationException("Max evaluation steps exceeded — possible cycle in policy graph");
            }
            String handle = evaluateNode(current, request, trace, traceLevel);
            String nextId = edgeMap.getOrDefault(current.getId(), Map.of()).get(handle);
            if (nextId == null) {
                throw new EvaluationException(
                        "No outgoing edge from node '" + current.getName() + "' [" + current.getId()
                        + "] on handle '" + handle + "'");
            }
            current = nodeMap.get(nextId);
            if (current == null) {
                throw new EvaluationException("Edge points to unknown node id: " + nextId);
            }
        }

        if (current.getType() == NodeType.CUSTOM_OUTPUT) {
            CustomOutputNodeConfig cfg = asConfig(current, CustomOutputNodeConfig.class);
            if (cfg == null || cfg.getTemplate() == null || cfg.getTemplate().isBlank()) {
                throw new EvaluationException("CUSTOM_OUTPUT node '" + current.getName() + "' has no template");
            }
            Object customOutput = customOutputTemplateEvaluator.evaluate(cfg.getTemplate(), request);
            return EvaluationResult.builder()
                    .policyId(policy.getId())
                    .policyVersion(policy.getVersion())
                    .policyType(PolicyType.RULE_CHAIN)
                    .customOutput(customOutput)
                    .triggeredBy(current.getName())
                    .ruleResults(traceLevel != TraceLevel.MINIMAL ? trace : null)
                    .evaluationMs(System.currentTimeMillis() - start)
                    .build();
        } else {
            OutcomeNodeConfig outcomeConfig = asConfig(current, OutcomeNodeConfig.class);
            Map<String, Object> outputFields = resolveOutcomeOutputFields(outcomeConfig, request);
            return EvaluationResult.builder()
                    .policyId(policy.getId())
                    .policyVersion(policy.getVersion())
                    .policyType(PolicyType.RULE_CHAIN)
                    .outcome(outcomeConfig.getOutcome())
                    .outputFields(outputFields.isEmpty() ? null : outputFields)
                    .triggeredBy(current.getName())
                    .ruleResults(traceLevel != TraceLevel.MINIMAL ? trace : null)
                    .evaluationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private String evaluateNode(PolicyNode node, EvaluationRequest request,
                                 List<RuleResult> trace, TraceLevel traceLevel) {
        return switch (node.getType()) {
            case START    -> "next";
            case RULE     -> evaluateRuleNode(node, request, trace, traceLevel);
            case BRANCH   -> evaluateBranchNode(node, request, trace, traceLevel);
            case SOURCE   -> evaluateSourceNode(node, request);
            case WORKFLOW -> evaluateWorkflowNode(node, request, trace, traceLevel);
            case MODEL    -> evaluateModelNode(node, request, trace, traceLevel);
            case OUTCOME       -> throw new EvaluationException("Internal error: evaluating OUTCOME node");
            case CUSTOM_OUTPUT -> throw new EvaluationException("Internal error: evaluating CUSTOM_OUTPUT as non-terminal node");
        };
    }

    // ── RULE node ─────────────────────────────────────────────────────────────

    private String evaluateRuleNode(PolicyNode node, EvaluationRequest request,
                                     List<RuleResult> trace, TraceLevel traceLevel) {
        RuleNodeConfig config = asConfig(node, RuleNodeConfig.class);
        if (config == null || config.getRules() == null || config.getRules().isEmpty()) {
            return "pass";
        }
        List<Rule> sorted = config.getRules().stream()
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .toList();

        for (Rule rule : sorted) {
            if (rule.getExpression() == null || rule.getExpression().isBlank()) continue;
            ExpressionNode ast;
            try {
                ast = expressionParser.parse(rule.getExpression());
            } catch (Exception e) {
                throw new EvaluationException("Rule '" + rule.getName()
                        + "' in node '" + node.getName() + "': parse error: " + e.getMessage(), e);
            }
            boolean result;
            try {
                Object val = expressionEvaluator.evaluate(ast, request);
                if (!(val instanceof Boolean b)) {
                    throw new EvaluationException("Rule '" + rule.getName() + "': must return boolean");
                }
                result = b;
            } catch (MissingValueException e) {
                throw new EvaluationException(
                        "Rule '" + rule.getName() + "' in node '" + node.getName()
                                + "': required field '" + e.getPath()
                                + "' is missing from context. Add it to the request or use"
                                + " cantDecideExpression to handle optional fields.");
            }

            // Write individual rule verdict into context so downstream BRANCH nodes
            // can reference it by name (e.g. "age_eligibility == true")
            request.getContext().put(rule.getName(), result);

            if (result) {
                if (traceLevel != TraceLevel.MINIMAL) {
                    trace.add(RuleResult.builder()
                            .name(node.getName() + " / " + rule.getName())
                            .expression(traceLevel == TraceLevel.FULL ? rule.getExpression() : null)
                            .result(true).action("pass").build());
                }
                continue;
            }
            // Main expression failed — check cantDecideExpression before returning fail
            String cantDecideExpr = rule.getCantDecideExpression();
            if (cantDecideExpr != null && !cantDecideExpr.isBlank()) {
                try {
                    ExpressionNode cdAst = expressionParser.parse(cantDecideExpr);
                    Object cdVal = expressionEvaluator.evaluate(cdAst, request);
                    if (Boolean.TRUE.equals(cdVal)) {
                        if (traceLevel != TraceLevel.MINIMAL) {
                            trace.add(RuleResult.builder()
                                    .name(node.getName() + " / " + rule.getName())
                                    .expression(traceLevel == TraceLevel.FULL ? cantDecideExpr : null)
                                    .result(false).action("cantDecide").build());
                        }
                        return "cantDecide";
                    }
                } catch (MissingValueException ignored) {
                    // cantDecide condition itself is missing data — treat as not triggered
                }
            }
            if (traceLevel != TraceLevel.MINIMAL) {
                trace.add(RuleResult.builder()
                        .name(node.getName() + " / " + rule.getName())
                        .expression(traceLevel == TraceLevel.FULL ? rule.getExpression() : null)
                        .result(false).action("fail").build());
            }
            return "fail";   // context already has rule.getName() = false from the write above
        }
        return "pass";
    }

    // ── BRANCH node ───────────────────────────────────────────────────────────

    private String evaluateBranchNode(PolicyNode node, EvaluationRequest request,
                                       List<RuleResult> trace, TraceLevel traceLevel) {
        BranchNodeConfig config = asConfig(node, BranchNodeConfig.class);
        if (config == null || config.getConditions() == null) return "default";

        for (BranchCondition condition : config.getConditions()) {
            if (condition.getExpression() == null || condition.getExpression().isBlank()) continue;
            try {
                ExpressionNode ast = expressionParser.parse(condition.getExpression());
                Object val = expressionEvaluator.evaluate(ast, request);
                if (Boolean.TRUE.equals(val)) {
                    if (traceLevel != TraceLevel.MINIMAL) {
                        trace.add(RuleResult.builder()
                                .name(node.getName() + " / " + condition.getLabel())
                                .expression(traceLevel == TraceLevel.FULL ? condition.getExpression() : null)
                                .result(true)
                                .action(condition.getId())
                                .build());
                    }
                    return condition.getId();
                }
            } catch (MissingValueException ignored) {
                // condition not met, continue to next
            }
        }
        return "default";
    }

    // ── SOURCE node ───────────────────────────────────────────────────────────

    /**
     * Loads each referenced lookup ID into request.getLookups() so that
     * downstream rule/branch expressions can resolve @lookupId references.
     * Uses the latest ACTIVE version of each lookup; falls back to the latest
     * version of any status if no ACTIVE version exists (e.g. still DRAFT).
     */
    private String evaluateSourceNode(PolicyNode node, EvaluationRequest request) {
        SourceNodeConfig config = asConfig(node, SourceNodeConfig.class);
        if (config == null || config.getSources() == null || config.getSources().isEmpty()) {
            return "next";
        }

        // Initialise the lookups map if the caller didn't provide one
        if (request.getLookups() == null) {
            request.setLookups(new HashMap<>());
        }

        for (String lookupId : config.getSources()) {
            // Skip if caller already supplied this lookup inline
            if (request.getLookups().containsKey(lookupId)) continue;

            // Prefer the latest ACTIVE version; fall back to latest of any status
            LookupDefinitionEntity entity =
                lookupStorageService.getLatestActive(lookupId)
                    .or(() -> lookupStorageService.getLatestAny(lookupId))
                    .orElseThrow(() -> new EvaluationException(
                        "SOURCE node references lookup '" + lookupId + "' which does not exist"));

            Lookup lookup = lookupStorageService.deserialize(entity);
            request.getLookups().put(lookupId, lookup);
        }
        return "next";
    }

    // ── WORKFLOW node ─────────────────────────────────────────────────────────

    private String evaluateWorkflowNode(PolicyNode node, EvaluationRequest request,
                                         List<RuleResult> trace, TraceLevel traceLevel) {
        WorkflowNodeConfig config = asConfig(node, WorkflowNodeConfig.class);
        if (config == null || config.getPolicyId() == null) {
            throw new EvaluationException("Workflow node '" + node.getName() + "' has no policyId");
        }
        EvaluateStoredRequest subReq = new EvaluateStoredRequest();
        subReq.setContext(request.getContext());
        subReq.setTraceLevel(request.getTraceLevel());

        EvaluationResult subResult = policyStorageService.evaluateSubPolicy(
                config.getPolicyId(), config.getVersion(), subReq, request.getActiveChain());

        String resultKey = (config.getResultKey() != null && !config.getResultKey().isBlank())
                ? config.getResultKey() : config.getPolicyId();
        Map<String, Object> injected = new HashMap<>();
        injected.put("outcome", subResult.getOutcome());
        if (subResult.getOutputFields() != null) injected.putAll(subResult.getOutputFields());
        if (subResult.getCustomOutput() != null) injected.put("customOutput", subResult.getCustomOutput());
        request.getContext().put(resultKey, injected);

        // Also inject under workflows['policyId version'] for CUSTOM_OUTPUT template access
        @SuppressWarnings("unchecked")
        Map<String, Object> workflowsMap = (Map<String, Object>)
                request.getContext().computeIfAbsent("workflows", k -> new HashMap<>());
        String wfKey = config.getPolicyId()
                + (config.getVersion() != null && !config.getVersion().isBlank()
                   ? " " + config.getVersion() : "");
        workflowsMap.put(wfKey, injected);
        workflowsMap.put(config.getPolicyId(), injected); // convenience: by policyId alone

        if (traceLevel != TraceLevel.MINIMAL) {
            trace.add(RuleResult.builder()
                    .name(node.getName() != null ? node.getName() : config.getPolicyId())
                    .expression("[workflow:" + config.getPolicyId() + "]")
                    .result(true)
                    .action(subResult.getOutcome())
                    .outcome(subResult.getOutcome())
                    .build());
        }
        return subResult.getOutcome() != null ? subResult.getOutcome() : "default";
    }

    // ── MODEL node ────────────────────────────────────────────────────────────

    private String evaluateModelNode(PolicyNode node, EvaluationRequest request,
                                      List<RuleResult> trace, TraceLevel traceLevel) {
        ModelNodeConfig config = asConfig(node, ModelNodeConfig.class);
        if (config == null || config.getModels() == null || config.getModels().isEmpty()) {
            return "next";
        }
        List<ModelEntry> sorted = config.getModels().stream()
                .sorted(Comparator.comparingInt(ModelEntry::getPriority))
                .toList();

        for (ModelEntry model : sorted) {
            String resultKey = (model.getResultKey() != null && !model.getResultKey().isBlank())
                    ? model.getResultKey() : model.getName();

            switch (model.getType()) {
                case "DECISION_TABLE" -> {
                    if (model.getInlineDefinition() == null) {
                        throw new EvaluationException("Model '" + model.getName()
                                + "' in node '" + node.getName() + "': inlineDefinition is required for DECISION_TABLE");
                    }
                    DecisionTable table = objectMapper.convertValue(model.getInlineDefinition(), DecisionTable.class);
                    if (table.getName() == null) table.setName(model.getName());

                    Policy tablePolicy = new Policy();
                    tablePolicy.setId(model.getName());
                    tablePolicy.setVersion("inline");
                    tablePolicy.setType(PolicyType.DECISION_TABLE);
                    tablePolicy.setTable(table);

                    EvaluationRequest subReq = new EvaluationRequest();
                    subReq.setPolicy(tablePolicy);
                    subReq.setContext(request.getContext());
                    subReq.setTraceLevel(request.getTraceLevel());

                    EvaluationResult result = decisionTableEvaluator.evaluate(subReq);

                    Map<String, Object> injected = new HashMap<>();
                    injected.put("output", result.getTableOutput());
                    if (result.getOutputColumn() != null) injected.put("outputColumn", result.getOutputColumn());
                    request.getContext().put(resultKey, injected);

                    if (traceLevel != TraceLevel.MINIMAL) {
                        trace.add(RuleResult.builder()
                                .name(node.getName() + " / " + model.getName())
                                .expression("[decision_table]")
                                .result(true).action("computed").outcome(resultKey).build());
                    }
                }
                case "SCORECARD" -> {
                    if (model.getInlineDefinition() == null) {
                        throw new EvaluationException("Model '" + model.getName()
                                + "' in node '" + node.getName() + "': inlineDefinition is required for SCORECARD");
                    }
                    Scorecard scorecard = objectMapper.convertValue(model.getInlineDefinition(), Scorecard.class);
                    if (scorecard.getName() == null) scorecard.setName(model.getName());

                    Policy scPolicy = new Policy();
                    scPolicy.setId(model.getName());
                    scPolicy.setVersion("inline");
                    scPolicy.setType(PolicyType.SCORECARD);
                    scPolicy.setScorecard(scorecard);

                    EvaluationRequest subReq = new EvaluationRequest();
                    subReq.setPolicy(scPolicy);
                    subReq.setContext(request.getContext());
                    subReq.setTraceLevel(request.getTraceLevel());

                    EvaluationResult result = scorecardEvaluator.evaluate(subReq);

                    // Write the outcome band string directly so branch conditions like
                    // "credit_risk_band == \"PRIME\"" resolve correctly
                    request.getContext().put(resultKey, result.getOutcome());

                    // Also write score details under resultKey_details for diagnostics
                    Map<String, Object> details = new HashMap<>();
                    details.put("score", result.getTotalScore());
                    details.put("label", result.getLabel());
                    if (result.getBreakdown() != null) details.put("breakdown", result.getBreakdown());
                    request.getContext().put(resultKey + "_details", details);

                    if (traceLevel != TraceLevel.MINIMAL) {
                        trace.add(RuleResult.builder()
                                .name(node.getName() + " / " + model.getName())
                                .expression("[scorecard]")
                                .result(true).action("computed").outcome(resultKey).build());
                    }
                }
                case "EXPRESSION" -> {
                    if (model.getExpression() == null || model.getExpression().isBlank()) {
                        throw new EvaluationException("Model '" + model.getName()
                                + "' in node '" + node.getName() + "': expression is required");
                    }
                    ExpressionNode ast;
                    try {
                        ast = expressionParser.parse(model.getExpression());
                    } catch (Exception e) {
                        throw new EvaluationException("Model '" + model.getName()
                                + "': parse error: " + e.getMessage(), e);
                    }
                    Object val = expressionEvaluator.evaluate(ast, request);
                    request.getContext().put(resultKey, val);

                    if (traceLevel != TraceLevel.MINIMAL) {
                        trace.add(RuleResult.builder()
                                .name(node.getName() + " / " + model.getName())
                                .expression(traceLevel == TraceLevel.FULL ? model.getExpression() : null)
                                .result(true).action("computed").outcome(resultKey).build());
                    }
                }
                default -> throw new EvaluationException("Unknown model type '" + model.getType()
                        + "' in node '" + node.getName() + "'");
            }
        }
        return "next";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> resolveOutcomeOutputFields(OutcomeNodeConfig config, EvaluationRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (config != null && config.getOutputFields() != null) {
            fields.putAll(config.getOutputFields());
        }
        if (config != null && config.getOutputExpressions() != null) {
            for (Map.Entry<String, String> entry : config.getOutputExpressions().entrySet()) {
                try {
                    ExpressionNode ast = expressionParser.parse(entry.getValue());
                    Object val = expressionEvaluator.evaluate(ast, request);
                    fields.put(entry.getKey(), val);
                } catch (Exception e) {
                    throw new EvaluationException(
                            "OUTCOME outputExpression '" + entry.getKey() + "': " + e.getMessage(), e);
                }
            }
        }
        return fields;
    }

    private Map<String, Map<String, String>> buildEdgeMap(List<PolicyEdge> edges) {
        Map<String, Map<String, String>> map = new HashMap<>();
        if (edges == null) return map;
        for (PolicyEdge edge : edges) {
            map.computeIfAbsent(edge.getSource(), k -> new HashMap<>())
               .put(edge.getSourceHandle(), edge.getTarget());
        }
        return map;
    }

    private <T> T asConfig(PolicyNode node, Class<T> type) {
        if (node.getConfig() == null) return null;
        return objectMapper.convertValue(node.getConfig(), type);
    }
}
