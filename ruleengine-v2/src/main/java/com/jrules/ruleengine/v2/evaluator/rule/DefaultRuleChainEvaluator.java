package com.jrules.ruleengine.v2.evaluator.rule;

import com.jrules.ruleengine.v2.evaluator.expression.ExpressionEvaluator;
import com.jrules.ruleengine.v2.evaluator.graph.GraphEvaluator;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.MissingValueException;
import com.jrules.ruleengine.v2.model.enums.OnMissing;
import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.enums.TraceLevel;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.model.result.RuleResult;
import com.jrules.ruleengine.v2.model.rule.Rule;
import com.jrules.ruleengine.v2.model.rule.RuleAction;
import com.jrules.ruleengine.v2.model.rule.WorkflowRef;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultRuleChainEvaluator implements RuleChainEvaluator {

    private final ExpressionEvaluator expressionEvaluator;
    private final ExpressionParser expressionParser;
    private final GraphEvaluator graphEvaluator;

    @Lazy
    @Autowired
    private PolicyStorageService policyStorageService;

    @Override
    public EvaluationResult evaluate(EvaluationRequest request) {
        // Delegate to graph evaluator for new graph-based policies
        if (request.getPolicy().isGraphBased()) {
            return graphEvaluator.evaluate(request);
        }
        long start = System.currentTimeMillis();

        List<Rule> rules = request.getPolicy().getRules();
        if (rules == null || rules.isEmpty()) {
            throw new EvaluationException("RULE_CHAIN policy has no rules defined");
        }

        List<Rule> sorted = rules.stream()
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .collect(Collectors.toList());

        TraceLevel trace = request.getTraceLevel() != null ? request.getTraceLevel() : TraceLevel.STANDARD;

        List<RuleResult> ruleResults  = new ArrayList<>();
        List<String>     skippedRules = new ArrayList<>();
        List<String>     notEvaluated = new ArrayList<>();

        String              outcome      = null;
        String              triggeredBy  = null;
        Map<String, Object> outputFields = null;
        boolean             stopped      = false;

        for (Rule rule : sorted) {
            if (stopped) {
                notEvaluated.add(rule.getName());
                continue;
            }

            // ── Workflow ref step ─────────────────────────────────────────────
            if (rule.getWorkflowRef() != null) {
                WorkflowRef ref = rule.getWorkflowRef();
                String resultKey = (ref.getResultKey() != null && !ref.getResultKey().isBlank())
                        ? ref.getResultKey() : ref.getPolicyId();

                EvaluateStoredRequest subReq = new EvaluateStoredRequest();
                subReq.setContext(request.getContext());
                subReq.setTraceLevel(request.getTraceLevel());

                EvaluationResult subResult = policyStorageService.evaluateSubPolicy(
                        ref.getPolicyId(), ref.getVersion(), subReq, request.getActiveChain());

                // Inject sub-result into context as { outcome, ...outputFields }
                Map<String, Object> injected = new HashMap<>();
                injected.put("outcome", subResult.getOutcome());
                if (subResult.getOutputFields() != null) {
                    injected.putAll(subResult.getOutputFields());
                }
                if (request.getContext() == null) {
                    request.setContext(new HashMap<>());
                }
                request.getContext().put(resultKey, injected);

                // Resolve routing action based on sub-policy outcome
                RuleAction action = null;
                if (ref.getOnOutcome() != null && subResult.getOutcome() != null) {
                    action = ref.getOnOutcome().get(subResult.getOutcome());
                }
                if (action == null) action = ref.getOnDefault();

                if (action != null && action.getType() == RuleAction.ActionType.STOP) {
                    outcome     = action.getOutcome() != null ? action.getOutcome() : subResult.getOutcome();
                    triggeredBy = rule.getName() != null ? rule.getName() : ref.getPolicyId();
                    outputFields = action.getOutputFields();
                    stopped = true;
                }

                if (trace != TraceLevel.MINIMAL) {
                    ruleResults.add(RuleResult.builder()
                            .name(rule.getName() != null ? rule.getName() : ref.getPolicyId())
                            .expression("[workflow:" + ref.getPolicyId() + "]")
                            .result(true)
                            .action(action != null ? action.getType().name() : "CONTINUE")
                            .outcome(stopped ? outcome : subResult.getOutcome())
                            .build());
                }
                continue;
            }

            // ── Regular rule step ─────────────────────────────────────────────
            ExpressionNode ast;
            try {
                ast = expressionParser.parse(rule.getExpression());
            } catch (Exception e) {
                throw new EvaluationException("Rule '" + rule.getName()
                        + "': failed to parse expression: " + e.getMessage(), e);
            }

            boolean result = false;
            try {
                Object value = expressionEvaluator.evaluate(ast, request);
                if (!(value instanceof Boolean b)) {
                    throw new EvaluationException("Rule '" + rule.getName()
                            + "': expression must evaluate to a boolean, got: "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
                }
                result = b;
            } catch (MissingValueException e) {
                result = applyOnMissing(rule, e, skippedRules, ruleResults, trace);
                if (rule.getOnMissing() == OnMissing.SKIP) continue;
            }

            RuleAction action = result ? rule.getOnPass() : rule.getOnFail();
            if (action != null && action.getType() == RuleAction.ActionType.STOP) {
                outcome      = action.getOutcome();
                triggeredBy  = rule.getName();
                outputFields = action.getOutputFields();
                stopped      = true;
            }

            if (trace != TraceLevel.MINIMAL) {
                ruleResults.add(RuleResult.builder()
                        .name(rule.getName())
                        .expression(trace == TraceLevel.FULL ? rule.getExpression() : null)
                        .result(result)
                        .action(action != null ? action.getType().name() : null)
                        .outcome(action != null ? action.getOutcome() : null)
                        .build());
            }
        }

        if (!stopped) {
            RuleAction defaultAction = request.getPolicy().getDefaultAction();
            if (defaultAction != null) {
                outcome      = defaultAction.getOutcome();
                triggeredBy  = "DEFAULT";
                outputFields = defaultAction.getOutputFields();
            }
        }

        return EvaluationResult.builder()
                .policyId(request.getPolicy().getId())
                .policyVersion(request.getPolicy().getVersion())
                .policyType(PolicyType.RULE_CHAIN)
                .outcome(outcome)
                .outputFields(outputFields)
                .triggeredBy(triggeredBy)
                .ruleResults(trace != TraceLevel.MINIMAL ? ruleResults : null)
                .skippedRules(skippedRules.isEmpty() ? null : skippedRules)
                .notEvaluated(notEvaluated.isEmpty() ? null : notEvaluated)
                .evaluationMs(System.currentTimeMillis() - start)
                .build();
    }

    private boolean applyOnMissing(Rule rule, MissingValueException e,
                                   List<String> skippedRules,
                                   List<RuleResult> ruleResults,
                                   TraceLevel trace) {
        switch (rule.getOnMissing()) {
            case FAIL -> {
                if (trace != TraceLevel.MINIMAL) {
                    ruleResults.add(RuleResult.builder()
                            .name(rule.getName())
                            .result(false)
                            .action("MISSING_FAIL")
                            .outcome(null)
                            .build());
                }
                return false;
            }
            case PASS -> {
                if (trace != TraceLevel.MINIMAL) {
                    ruleResults.add(RuleResult.builder()
                            .name(rule.getName())
                            .result(true)
                            .action("MISSING_PASS")
                            .outcome(null)
                            .build());
                }
                return true;
            }
            case SKIP -> {
                skippedRules.add(rule.getName());
                return false;
            }
            default -> throw new EvaluationException("Unknown onMissing value for rule: " + rule.getName());
        }
    }
}
