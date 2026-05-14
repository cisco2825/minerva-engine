package com.jrules.ruleengine.v2.evaluator.expression;

import com.jrules.ruleengine.v2.evaluator.table.DecisionTableEvaluator;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.MissingValueException;
import com.jrules.ruleengine.v2.function.BuiltinFunction;
import com.jrules.ruleengine.v2.function.FunctionRegistry;
import com.jrules.ruleengine.v2.lookup.LookupResolver;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.model.udf.UDF;
import com.jrules.ruleengine.v2.parser.ExpressionParser;
import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;
import com.jrules.ruleengine.v2.parser.ast.Nodes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultExpressionEvaluator implements ExpressionEvaluator {

    private final FunctionRegistry functionRegistry;
    private final LookupResolver lookupResolver;
    private final ExpressionParser expressionParser;

    // Lazy to break the circular dependency: ExpressionEvaluator ↔ DecisionTableEvaluator
    @Lazy
    @Autowired
    private DecisionTableEvaluator decisionTableEvaluator;

    @Override
    public Object evaluate(ExpressionNode ast, EvaluationRequest request) {
        return eval(ast, request);
    }

    // ── AST walker ────────────────────────────────────────────────────────────

    private Object eval(ExpressionNode node, EvaluationRequest request) {
        if (node instanceof Nodes.NumberLiteralNode  n) return n.value;
        if (node instanceof Nodes.StringLiteralNode  n) return n.value;
        if (node instanceof Nodes.BooleanLiteralNode n) return n.value;
        if (node instanceof Nodes.NullLiteralNode    n) return null;
        if (node instanceof Nodes.ContextPathNode    n) return resolveContext(n, request);
        if (node instanceof Nodes.LookupRefNode      n) return lookupResolver.resolve(n.name, request.getLookups());
        if (node instanceof Nodes.ListLiteralNode    n) return evalList(n, request);
        if (node instanceof Nodes.UnaryOpNode        n) return evalUnary(n, request);
        if (node instanceof Nodes.BinaryOpNode       n) return evalBinary(n, request);
        if (node instanceof Nodes.TernaryNode        n) return evalTernary(n, request);
        if (node instanceof Nodes.BetweenNode        n) return evalBetween(n, request);
        if (node instanceof Nodes.InNode             n) return evalIn(n, request);
        if (node instanceof Nodes.IsNullNode         n) return evalIsNull(n, request);
        if (node instanceof Nodes.StringOpNode       n) return evalStringOp(n, request);
        if (node instanceof Nodes.FunctionCallNode   n) return evalFunction(n, request);
        throw new EvaluationException("Unknown AST node type: " + node.getClass().getSimpleName());
    }

    // ── Context resolution ────────────────────────────────────────────────────

    private Object resolveContext(Nodes.ContextPathNode node, EvaluationRequest request) {
        Map<String, Object> ctx = request.getContext();
        if (ctx == null) throw new MissingValueException(String.join(".", node.segments));
        Object current = ctx;
        for (String segment : node.segments) {
            if (!(current instanceof Map<?, ?> map)) {
                throw new MissingValueException(String.join(".", node.segments));
            }
            if (!map.containsKey(segment)) {
                throw new MissingValueException(String.join(".", node.segments));
            }
            current = map.get(segment);
        }
        return current;
    }

    // ── Node evaluators ───────────────────────────────────────────────────────

    private List<Object> evalList(Nodes.ListLiteralNode node, EvaluationRequest request) {
        return node.elements.stream()
                .map(e -> eval(e, request))
                .collect(Collectors.toList());
    }

    private Object evalUnary(Nodes.UnaryOpNode node, EvaluationRequest request) {
        Object operand = eval(node.operand, request);
        return switch (node.operator) {
            case "NOT" -> !toBoolean(operand, node);
            case "-"   -> -toNumber(operand, node);
            default    -> throw new EvaluationException("Unknown unary operator: " + node.operator);
        };
    }

    private Object evalBinary(Nodes.BinaryOpNode node, EvaluationRequest request) {
        // Short-circuit logical operators
        if ("AND".equals(node.operator)) {
            return toBoolean(eval(node.left, request), node) && toBoolean(eval(node.right, request), node);
        }
        if ("OR".equals(node.operator)) {
            return toBoolean(eval(node.left, request), node) || toBoolean(eval(node.right, request), node);
        }

        Object left  = eval(node.left,  request);
        Object right = eval(node.right, request);

        return switch (node.operator) {
            // Arithmetic
            case "+" -> {
                if (left instanceof String || right instanceof String) {
                    yield (left == null ? "" : left.toString()) + (right == null ? "" : right.toString());
                }
                yield toNumber(left, node) + toNumber(right, node);
            }
            case "-" -> toNumber(left, node) - toNumber(right, node);
            case "*" -> toNumber(left, node) * toNumber(right, node);
            case "/" -> {
                double d = toNumber(right, node);
                if (d == 0) throw new EvaluationException("Division by zero");
                yield toNumber(left, node) / d;
            }
            case "%" -> toNumber(left, node) % toNumber(right, node);
            // Comparison
            case "=", "==" -> compareEqual(left, right);
            case "!=", "<>" -> !compareEqual(left, right);
            case "<"  -> compareOrdered(left, right, node) < 0;
            case "<=" -> compareOrdered(left, right, node) <= 0;
            case ">"  -> compareOrdered(left, right, node) > 0;
            case ">=" -> compareOrdered(left, right, node) >= 0;
            default -> throw new EvaluationException("Unknown binary operator: " + node.operator);
        };
    }

    private Object evalTernary(Nodes.TernaryNode node, EvaluationRequest request) {
        boolean cond = toBoolean(eval(node.condition, request), node);
        return cond ? eval(node.thenExpr, request) : eval(node.elseExpr, request);
    }

    private Object evalBetween(Nodes.BetweenNode node, EvaluationRequest request) {
        Object value = eval(node.value,  request);
        Object low   = eval(node.low,    request);
        Object high  = eval(node.high,   request);
        boolean inRange = compareOrdered(value, low, node) >= 0
                       && compareOrdered(value, high, node) <= 0;
        return node.negated ? !inRange : inRange;
    }

    @SuppressWarnings("unchecked")
    private Object evalIn(Nodes.InNode node, EvaluationRequest request) {
        Object value = eval(node.value, request);
        Object listOrLookup = eval(node.listOrLookup, request);
        if (!(listOrLookup instanceof List<?> list)) {
            throw new EvaluationException("IN operand must be a list or lookup, got: " + listOrLookup);
        }
        boolean found = list.stream().anyMatch(item -> compareEqual(value, item));
        return node.negated ? !found : found;
    }

    private Object evalIsNull(Nodes.IsNullNode node, EvaluationRequest request) {
        Object value;
        try {
            value = eval(node.value, request);
        } catch (MissingValueException e) {
            // Missing and IS NULL → true; Missing and IS NOT NULL → false
            return !node.negated;
        }
        boolean isNull = (value == null);
        return node.negated ? !isNull : isNull;
    }

    private Object evalStringOp(Nodes.StringOpNode node, EvaluationRequest request) {
        String value   = toString(eval(node.value,   request), node);
        String pattern = toString(eval(node.pattern, request), node);
        return switch (node.operator) {
            case "CONTAINS"    -> value.contains(pattern);
            case "STARTS_WITH" -> value.startsWith(pattern);
            case "ENDS_WITH"   -> value.endsWith(pattern);
            case "MATCHES"     -> value.matches(pattern);
            default -> throw new EvaluationException("Unknown string operator: " + node.operator);
        };
    }

    private Object evalFunction(Nodes.FunctionCallNode node, EvaluationRequest request) {
        String name = node.name.toUpperCase();

        // Special case: TABLE(tableName, arg1, arg2, ...)
        if ("TABLE".equals(name)) {
            return evalTableFunction(node, request);
        }

        // Evaluate arguments first
        List<Object> args = node.arguments.stream()
                .map(a -> eval(a, request))
                .collect(Collectors.toList());

        // Built-in lookup
        Optional<BuiltinFunction> builtin = functionRegistry.getBuiltin(name);
        if (builtin.isPresent()) {
            return builtin.get().invoke(args);
        }

        // UDF lookup from request
        if (request.getUdfs() != null) {
            Optional<UDF> udf = request.getUdfs().stream()
                    .filter(u -> name.equalsIgnoreCase(u.getName()))
                    .findFirst();
            if (udf.isPresent()) {
                return evalUdf(udf.get(), args, request);
            }
        }

        throw new EvaluationException("Unknown function: " + name
                + ". Known functions: " + functionRegistry.allKnownNames());
    }

    private Object evalTableFunction(Nodes.FunctionCallNode node, EvaluationRequest request) {
        if (node.arguments.isEmpty()) {
            throw new EvaluationException("TABLE() requires at least one argument (table name)");
        }
        Object tableNameObj = eval(node.arguments.get(0), request);
        if (!(tableNameObj instanceof String tableName)) {
            throw new EvaluationException("TABLE() first argument must be a string (table name)");
        }
        Map<String, DecisionTable> tables = request.getTables();
        if (tables == null || !tables.containsKey(tableName)) {
            throw new EvaluationException("TABLE() references unknown table: '" + tableName + "'");
        }
        List<Object> inputValues = node.arguments.subList(1, node.arguments.size())
                .stream()
                .map(a -> eval(a, request))
                .collect(Collectors.toList());
        return decisionTableEvaluator.evaluateInline(tables.get(tableName), inputValues);
    }

    private Object evalUdf(UDF udf, List<Object> args, EvaluationRequest request) {
        if (udf.getParams() != null && udf.getParams().size() != args.size()) {
            throw new EvaluationException("UDF '" + udf.getName() + "' expects "
                    + udf.getParams().size() + " argument(s), got " + args.size());
        }
        // Build a mini context from param names → evaluated arg values
        Map<String, Object> udfContext = new HashMap<>();
        if (udf.getParams() != null) {
            for (int i = 0; i < udf.getParams().size(); i++) {
                udfContext.put(udf.getParams().get(i).getName(), args.get(i));
            }
        }
        EvaluationRequest udfRequest = new EvaluationRequest();
        udfRequest.setContext(udfContext);
        ExpressionNode udfAst = expressionParser.parse(udf.getExpression());
        return eval(udfAst, udfRequest);
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    private boolean compareEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        // Both numeric → numeric equality
        if (a instanceof Number && b instanceof Number) {
            return toDouble(a) == toDouble(b);
        }
        return a.equals(b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareOrdered(Object a, Object b, ExpressionNode ctx) {
        if (a == null || b == null) {
            throw new EvaluationException("Cannot compare null values with < > <= >=");
        }
        // Numeric
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(toDouble(a), toDouble(b));
        }
        // Date strings (yyyy-MM-dd)
        if (a instanceof String sa && b instanceof String sb) {
            try {
                LocalDate da = LocalDate.parse(sa);
                LocalDate db = LocalDate.parse(sb);
                return da.compareTo(db);
            } catch (Exception ignored) {}
            // Fall through to lexicographic
        }
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) {
            return ca.compareTo(b);
        }
        throw new EvaluationException("Cannot order-compare values of types "
                + a.getClass().getSimpleName() + " and " + b.getClass().getSimpleName());
    }

    private boolean toBoolean(Object v, ExpressionNode ctx) {
        if (v instanceof Boolean b) return b;
        throw new EvaluationException("Expected boolean but got: "
                + (v == null ? "null" : v.getClass().getSimpleName()));
    }

    private double toNumber(Object v, ExpressionNode ctx) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) {
                throw new EvaluationException("Cannot convert '" + s + "' to a number");
            }
        }
        throw new EvaluationException("Expected number but got: "
                + (v == null ? "null" : v.getClass().getSimpleName()));
    }

    private String toString(Object v, ExpressionNode ctx) {
        if (v == null) throw new EvaluationException("Expected string but got null");
        return v.toString();
    }

    private double toDouble(Object v) {
        return ((Number) v).doubleValue();
    }
}
