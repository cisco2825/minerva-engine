package com.jrules.ruleengine.v2.evaluator.scorecard;

import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.MissingValueException;
import com.jrules.ruleengine.v2.model.enums.OnMissing;
import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.model.result.ScorecardBreakdownEntry;
import com.jrules.ruleengine.v2.model.scorecard.ScoreBand;
import com.jrules.ruleengine.v2.model.scorecard.Scorecard;
import com.jrules.ruleengine.v2.model.scorecard.ScorecardVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultScorecardEvaluator implements ScorecardEvaluator {

    @Override
    public EvaluationResult evaluate(EvaluationRequest request) {
        long start = System.currentTimeMillis();
        Scorecard scorecard = request.getPolicy().getScorecard();
        if (scorecard == null) throw new EvaluationException("SCORECARD policy has no scorecard defined");

        double totalScore = 0;
        double maxPossibleScore = 0;
        List<ScorecardBreakdownEntry> breakdown = new ArrayList<>();
        List<String> skippedVariables = new ArrayList<>();

        for (ScorecardVariable variable : scorecard.getVariables()) {
            // Resolve the variable's value from context
            Object value;
            try {
                value = resolveParam(variable.getParam(), request.getContext());
            } catch (MissingValueException e) {
                value = null;
            }

            if (value == null) {
                switch (variable.getOnMissing()) {
                    case FAIL -> throw new EvaluationException(
                            "Scorecard variable '" + variable.getName() + "': missing value for param '" + variable.getParam() + "'");
                    case PASS -> {
                        // Use defaultPoints for this variable
                        double pts = variable.getDefaultPoints();
                        totalScore += pts;
                        maxPossibleScore += maxBandPoints(variable);
                        breakdown.add(ScorecardBreakdownEntry.builder()
                                .variable(variable.getName())
                                .value(null)
                                .pointsAwarded(pts)
                                .label("default (missing)")
                                .build());
                    }
                    case SKIP -> skippedVariables.add(variable.getName());
                }
                continue;
            }

            // Find first matching band
            ScoreBand matched = findMatchingBand(variable, value);
            double pts = matched != null ? matched.getPoints() : variable.getDefaultPoints();
            String label = matched != null ? matched.getLabel() : "no match (default)";

            totalScore += pts;
            maxPossibleScore += maxBandPoints(variable);

            breakdown.add(ScorecardBreakdownEntry.builder()
                    .variable(variable.getName())
                    .value(value)
                    .pointsAwarded(pts)
                    .label(label)
                    .build());
        }

        // Map total score to threshold outcome
        String outcome = resolveOutcome(totalScore, scorecard);
        String label   = resolveLabel(totalScore, scorecard);

        return EvaluationResult.builder()
                .policyId(request.getPolicy().getId())
                .policyVersion(request.getPolicy().getVersion())
                .policyType(PolicyType.SCORECARD)
                .outcome(outcome)
                .totalScore(totalScore)
                .maxPossibleScore(maxPossibleScore)
                .breakdown(breakdown)
                .skippedVariables(skippedVariables)
                .label(label)
                .evaluationMs(System.currentTimeMillis() - start)
                .build();
    }

    // ── Band matching ─────────────────────────────────────────────────────────

    private ScoreBand findMatchingBand(ScorecardVariable variable, Object value) {
        for (ScoreBand band : variable.getBands()) {
            if (bandMatches(band, value)) return band;
        }
        return null;
    }

    private boolean bandMatches(ScoreBand band, Object value) {
        Object expected = band.getValue();
        List<Object> expectedList = band.getValues();
        return switch (band.getOperator()) {
            case EQ  -> compareEqual(value, expected);
            case NEQ -> !compareEqual(value, expected);
            case GT  -> compareOrdered(value, expected) > 0;
            case GTE -> compareOrdered(value, expected) >= 0;
            case LT  -> compareOrdered(value, expected) < 0;
            case LTE -> compareOrdered(value, expected) <= 0;
            case BT  -> {
                Object lo = expectedList.get(0), hi = expectedList.get(1);
                yield compareOrdered(value, lo) >= 0 && compareOrdered(value, hi) <= 0;
            }
            case IN     -> expectedList.stream().anyMatch(v -> compareEqual(value, v));
            case NOT_IN -> expectedList.stream().noneMatch(v -> compareEqual(value, v));
            default -> throw new EvaluationException("Unsupported operator in score band: " + band.getOperator());
        };
    }

    private double maxBandPoints(ScorecardVariable variable) {
        if (variable.getBands() == null || variable.getBands().isEmpty()) return variable.getDefaultPoints();
        return variable.getBands().stream()
                .mapToDouble(ScoreBand::getPoints)
                .max()
                .orElse(variable.getDefaultPoints());
    }

    // ── Threshold resolution ──────────────────────────────────────────────────

    private String resolveOutcome(double score, Scorecard scorecard) {
        if (scorecard.getThresholds() == null) return scorecard.getDefaultOutcome();
        return scorecard.getThresholds().stream()
                .filter(t -> score >= t.getMin() && score <= t.getMax())
                .map(com.jrules.ruleengine.v2.model.scorecard.ScoreThreshold::getOutcome)
                .findFirst()
                .orElse(scorecard.getDefaultOutcome());
    }

    private String resolveLabel(double score, Scorecard scorecard) {
        if (scorecard.getThresholds() == null) return null;
        return scorecard.getThresholds().stream()
                .filter(t -> score >= t.getMin() && score <= t.getMax())
                .map(com.jrules.ruleengine.v2.model.scorecard.ScoreThreshold::getLabel)
                .findFirst()
                .orElse(null);
    }

    // ── Context helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object resolveParam(String param, Map<String, Object> context) {
        if (context == null) throw new MissingValueException(param);
        String[] segments = param.split("\\.");
        Object current = context;
        for (String seg : segments) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(seg)) {
                throw new MissingValueException(param);
            }
            current = map.get(seg);
        }
        return current;
    }

    private boolean compareEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number)
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        return a.equals(b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareOrdered(Object a, Object b) {
        if (a == null || b == null) throw new EvaluationException("Cannot compare null values");
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        if (a instanceof String sa && b instanceof String sb) {
            try { return LocalDate.parse(sa).compareTo(LocalDate.parse(sb)); }
            catch (Exception ignored) {}
        }
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) return ca.compareTo(b);
        throw new EvaluationException("Cannot compare types: " + a.getClass().getSimpleName());
    }
}
