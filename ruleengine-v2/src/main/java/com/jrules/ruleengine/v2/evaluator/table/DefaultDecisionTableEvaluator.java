package com.jrules.ruleengine.v2.evaluator.table;

import com.jrules.ruleengine.v2.evaluator.expression.ExpressionEvaluator;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.MissingValueException;
import com.jrules.ruleengine.v2.model.enums.HitPolicy;
import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.model.table.CellCondition;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.model.table.InputColumn;
import com.jrules.ruleengine.v2.model.table.TableRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultDecisionTableEvaluator implements DecisionTableEvaluator {

    private final ExpressionEvaluator expressionEvaluator;

    // ── Top-level policy evaluation ───────────────────────────────────────────

    @Override
    public EvaluationResult evaluate(EvaluationRequest request) {
        long start = System.currentTimeMillis();
        DecisionTable table = request.getPolicy().getTable();
        if (table == null) throw new EvaluationException("DECISION_TABLE policy has no table defined");

        // Resolve input values from context expressions in the input columns
        List<Object> inputValues = resolveInputValues(table, request);
        Object output = evaluateInline(table, inputValues);

        // Build tableInputs map for tracing
        Map<String, Object> tableInputs = new LinkedHashMap<>();
        for (int i = 0; i < table.getInputs().size(); i++) {
            tableInputs.put(table.getInputs().get(i).getName(), inputValues.get(i));
        }

        return EvaluationResult.builder()
                .policyId(request.getPolicy().getId())
                .policyVersion(request.getPolicy().getVersion())
                .policyType(PolicyType.DECISION_TABLE)
                .outcome(output == null ? null : output.toString())
                .tableOutput(output)
                .outputColumn(table.getOutput() != null ? table.getOutput().getName() : null)
                .tableInputs(tableInputs)
                .evaluationMs(System.currentTimeMillis() - start)
                .build();
    }

    // ── Inline evaluation (used by TABLE() built-in) ──────────────────────────

    @Override
    public Object evaluateInline(DecisionTable table, List<Object> inputValues) {
        validateInputCount(table, inputValues);

        List<TableRow> sortedRows = table.getRows().stream()
                .sorted(Comparator.comparingInt(TableRow::getPriority))
                .collect(Collectors.toList());

        List<TableRow> matched = new ArrayList<>();

        for (TableRow row : sortedRows) {
            if (rowMatches(row, table.getInputs(), inputValues)) {
                matched.add(row);
                if (table.getHitPolicy() == HitPolicy.FIRST) {
                    return row.getOutput();
                }
            }
        }

        if (matched.isEmpty()) return null;

        // UNIQUE: warn if more than one row matched but return the first
        if (matched.size() > 1) {
            throw new EvaluationException("DECISION_TABLE '" + table.getName()
                    + "' with UNIQUE hit policy matched " + matched.size()
                    + " rows. Rows must be mutually exclusive.");
        }
        return matched.get(0).getOutput();
    }

    // ── Row matching ──────────────────────────────────────────────────────────

    private boolean rowMatches(TableRow row, List<InputColumn> columns, List<Object> inputValues) {
        List<CellCondition> conditions = row.getConditions();
        for (int i = 0; i < conditions.size(); i++) {
            CellCondition cell = conditions.get(i);
            if (cell.getType() == CellCondition.CellType.ANY) continue;
            Object actual = inputValues.get(i);
            if (!cellMatches(cell, actual)) return false;
        }
        return true;
    }

    private boolean cellMatches(CellCondition cell, Object actual) {
        Object expected = cell.getValue();
        List<Object> expectedList = cell.getValues();

        return switch (cell.getOperator()) {
            case EQ  -> compareEqual(actual, expected);
            case NEQ -> !compareEqual(actual, expected);
            case GT  -> compareOrdered(actual, expected) > 0;
            case GTE -> compareOrdered(actual, expected) >= 0;
            case LT  -> compareOrdered(actual, expected) < 0;
            case LTE -> compareOrdered(actual, expected) <= 0;
            case BT  -> {
                Object lo = expectedList.get(0), hi = expectedList.get(1);
                yield compareOrdered(actual, lo) >= 0 && compareOrdered(actual, hi) <= 0;
            }
            case IN     -> expectedList.stream().anyMatch(v -> compareEqual(actual, v));
            case NOT_IN -> expectedList.stream().noneMatch(v -> compareEqual(actual, v));
            case CONTAINS   -> toStr(actual).contains(toStr(expected));
            case STARTS_WITH -> toStr(actual).startsWith(toStr(expected));
            case ENDS_WITH   -> toStr(actual).endsWith(toStr(expected));
            case MATCHES     -> toStr(actual).matches(toStr(expected));
            case BEFORE -> compareOrdered(actual, expected) < 0;
            case AFTER  -> compareOrdered(actual, expected) > 0;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Object> resolveInputValues(DecisionTable table, EvaluationRequest request) {
        return table.getInputs().stream()
                .map(col -> resolveParam(col.getParam(), request.getContext()))
                .collect(Collectors.toList());
    }

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

    private void validateInputCount(DecisionTable table, List<Object> inputValues) {
        int expected = table.getInputs() == null ? 0 : table.getInputs().size();
        if (inputValues.size() != expected) {
            throw new EvaluationException("Table '" + table.getName() + "' expects "
                    + expected + " input(s), got " + inputValues.size());
        }
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
            try {
                return LocalDate.parse(sa).compareTo(LocalDate.parse(sb));
            } catch (Exception ignored) {}
        }
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) return ca.compareTo(b);
        throw new EvaluationException("Cannot compare " + a.getClass().getSimpleName()
                + " with " + b.getClass().getSimpleName());
    }

    private String toStr(Object v) {
        return v == null ? "" : v.toString();
    }
}
