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

    private final FunctionRegistry  functionRegistry;
    private final LookupResolver    lookupResolver;
    private final ExpressionParser  expressionParser;

    // Lazy to break the circular dependency: ExpressionEvaluator ↔ DecisionTableEvaluator
    @Lazy
    @Autowired
    private DecisionTableEvaluator decisionTableEvaluator;

    /**
     * Thread-local scope stack for {@code let} variable bindings.
     *
     * <p>Each frame ({@link Map}) holds the variables declared in one {@code let}
     * block. Frames are pushed before evaluating the block's body and popped in a
     * {@code finally} clause, so the stack is always clean even on exceptions.
     * The deque is ordered innermost-first (head = innermost), which gives correct
     * shadowing: inner {@code let} bindings shadow outer ones (and both shadow the
     * request context) when the same name is used.
     */
    private static final ThreadLocal<Deque<Map<String, Object>>> LET_SCOPE =
            ThreadLocal.withInitial(ArrayDeque::new);

    // ── Public API ────────────────────────────────────────────────────────────

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
        if (node instanceof Nodes.LetBlockNode       n) return evalLetBlock(n, request);
        if (node instanceof Nodes.SubscriptNode      n) return evalSubscript(n, request);
        if (node instanceof Nodes.FieldAccessNode    n) return evalFieldAccess(n, request);
        throw new EvaluationException("Unknown AST node type: " + node.getClass().getSimpleName());
    }

    // ── Context resolution ────────────────────────────────────────────────────

    /**
     * Resolve a dotted context path to a value.
     *
     * <p>Single-segment names (e.g. {@code x}) are checked in the {@code let}
     * scope stack first (innermost frame first), so {@code let}-bound variables
     * naturally shadow request-context keys with the same name.
     * Multi-segment paths (e.g. {@code applicant.age}) always resolve against
     * the request context.
     */
    private Object resolveContext(Nodes.ContextPathNode node, EvaluationRequest request) {
        // Check let scope for single-segment names
        if (node.segments.size() == 1) {
            String name = node.segments.get(0);
            for (Map<String, Object> frame : LET_SCOPE.get()) {
                if (frame.containsKey(name)) {
                    return frame.get(name);
                }
            }
        }

        // Resolve against request context
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
            return toBoolean(eval(node.left, request), node)
                    && toBoolean(eval(node.right, request), node);
        }
        if ("OR".equals(node.operator)) {
            return toBoolean(eval(node.left, request), node)
                    || toBoolean(eval(node.right, request), node);
        }

        Object left  = eval(node.left,  request);
        Object right = eval(node.right, request);

        return switch (node.operator) {
            // Arithmetic
            case "+" -> {
                if (left instanceof String || right instanceof String) {
                    yield (left == null ? "" : left.toString())
                            + (right == null ? "" : right.toString());
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
            case "<"  -> compareOrdered(left, right, node) <  0;
            case "<=" -> compareOrdered(left, right, node) <= 0;
            case ">"  -> compareOrdered(left, right, node) >  0;
            case ">=" -> compareOrdered(left, right, node) >= 0;
            default -> throw new EvaluationException("Unknown binary operator: " + node.operator);
        };
    }

    private Object evalTernary(Nodes.TernaryNode node, EvaluationRequest request) {
        boolean cond = toBoolean(eval(node.condition, request), node);
        return cond ? eval(node.thenExpr, request) : eval(node.elseExpr, request);
    }

    private Object evalBetween(Nodes.BetweenNode node, EvaluationRequest request) {
        Object value = eval(node.value, request);
        Object low   = eval(node.low,   request);
        Object high  = eval(node.high,  request);
        boolean inRange = compareOrdered(value, low,  node) >= 0
                       && compareOrdered(value, high, node) <= 0;
        return node.negated ? !inRange : inRange;
    }

    @SuppressWarnings("unchecked")
    private Object evalIn(Nodes.InNode node, EvaluationRequest request) {
        Object value         = eval(node.value,         request);
        Object listOrLookup  = eval(node.listOrLookup,  request);
        if (!(listOrLookup instanceof List<?> list)) {
            throw new EvaluationException(
                    "IN operand must be a list or lookup, got: " + listOrLookup);
        }
        boolean found = list.stream().anyMatch(item -> compareEqual(value, item));
        return node.negated ? !found : found;
    }

    private Object evalIsNull(Nodes.IsNullNode node, EvaluationRequest request) {
        Object value;
        try {
            value = eval(node.value, request);
        } catch (MissingValueException e) {
            // A missing field is treated as null — IS NULL → true; IS NOT NULL → false
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

    // ── Let block evaluation ──────────────────────────────────────────────────

    /**
     * Evaluate a {@code let} block:
     * <ol>
     *   <li>Push a new, empty scope frame onto the thread-local stack.</li>
     *   <li>Evaluate each binding's expression in order, storing results in the frame.
     *       Because the frame is already on the stack, later bindings can reference
     *       earlier ones by name.</li>
     *   <li>Evaluate and return the body expression.</li>
     *   <li>Pop the frame in {@code finally} — the stack is always left clean.</li>
     * </ol>
     */
    private Object evalLetBlock(Nodes.LetBlockNode node, EvaluationRequest request) {
        Map<String, Object> frame = new HashMap<>(node.bindings.size() * 2);
        Deque<Map<String, Object>> stack = LET_SCOPE.get();
        stack.addFirst(frame); // push — innermost frame is at the head
        try {
            for (Nodes.LetBlockNode.LetBinding binding : node.bindings) {
                Object value = eval(binding.expression, request);
                frame.put(binding.name, value);
            }
            return eval(node.body, request);
        } finally {
            stack.removeFirst(); // always pop, even on exception
        }
    }

    // ── Function evaluation ───────────────────────────────────────────────────

    private Object evalFunction(Nodes.FunctionCallNode node, EvaluationRequest request) {
        String name = node.name.toUpperCase();

        // ── Special cases: lazy argument evaluation ───────────────────────────

        // TABLE(tableName, arg1, arg2, ...) — first arg must be a string table name
        if ("TABLE".equals(name)) {
            return evalTableFunction(node, request);
        }

        // Type-inspection functions must catch MissingValueException from argument eval
        if ("ISNULL".equals(name) || "ISNUMBER".equals(name) || "ISSTRING".equals(name)) {
            return evalTypeCheckFunction(name, node, request);
        }

        // IFELSE — lazy: only evaluates the branch that's needed
        if ("IFELSE".equals(name)) {
            return evalIfElse(node, request);
        }

        // ── Eager evaluation ─────────────────────────────────────────────────

        List<Object> args = node.arguments.stream()
                .map(a -> eval(a, request))
                .collect(Collectors.toList());

        // Built-in registry
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

    /**
     * Evaluate ISNULL / ISNUMBER / ISSTRING with lazy argument evaluation.
     * A missing context path is treated as null (ISNULL → true, others → false).
     */
    private Object evalTypeCheckFunction(String name,
                                         Nodes.FunctionCallNode node,
                                         EvaluationRequest request) {
        if (node.arguments.size() != 1) {
            throw new EvaluationException(
                    name + "() requires exactly 1 argument, got " + node.arguments.size());
        }
        Object value;
        try {
            value = eval(node.arguments.get(0), request);
        } catch (MissingValueException e) {
            // Missing field: treat as null for type checks
            return "ISNULL".equals(name); // true for ISNULL, false for ISNUMBER/ISSTRING
        }
        return switch (name) {
            case "ISNULL"   -> value == null;
            case "ISNUMBER" -> value instanceof Number;
            case "ISSTRING" -> value instanceof String;
            default -> throw new EvaluationException("Unexpected type-check function: " + name);
        };
    }

    private Object evalTableFunction(Nodes.FunctionCallNode node, EvaluationRequest request) {
        if (node.arguments.isEmpty()) {
            throw new EvaluationException(
                    "TABLE() requires at least one argument (the table name)");
        }
        Object tableNameObj = eval(node.arguments.get(0), request);
        if (!(tableNameObj instanceof String tableName)) {
            throw new EvaluationException(
                    "TABLE() first argument must be a string (table name)");
        }
        Map<String, DecisionTable> tables = request.getTables();
        if (tables == null || !tables.containsKey(tableName)) {
            throw new EvaluationException(
                    "TABLE() references unknown table: '" + tableName + "'");
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

    // ── Subscript, field access, and IFELSE ───────────────────────────────────

    private Object evalSubscript(Nodes.SubscriptNode node, EvaluationRequest request) {
        Object base = eval(node.base, request);
        Object key  = eval(node.key,  request);
        if (base instanceof Map<?,?> map) {
            String k = key instanceof Number n ? String.valueOf(n.intValue()) : String.valueOf(key);
            return map.get(k);  // return null if key not found (workflow may not have run yet)
        }
        if (base instanceof List<?> list && key instanceof Number n) {
            return list.get((int) n.doubleValue());
        }
        throw new EvaluationException("Cannot subscript "
                + (base == null ? "null" : base.getClass().getSimpleName())
                + " with key '" + key + "'");
    }

    private Object evalFieldAccess(Nodes.FieldAccessNode node, EvaluationRequest request) {
        Object base = eval(node.base, request);
        if (base instanceof Map<?,?> map) {
            if (!map.containsKey(node.field)) throw new MissingValueException(node.field);
            return map.get(node.field);
        }
        throw new EvaluationException("Cannot access field '" + node.field + "' on "
                + (base == null ? "null" : base.getClass().getSimpleName()));
    }

    private Object evalIfElse(Nodes.FunctionCallNode node, EvaluationRequest request) {
        if (node.arguments.size() != 3) {
            throw new EvaluationException(
                    "IFELSE() requires exactly 3 arguments, got " + node.arguments.size());
        }
        boolean cond = toBoolean(eval(node.arguments.get(0), request), node);
        return cond ? eval(node.arguments.get(1), request) : eval(node.arguments.get(2), request);
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    private boolean compareEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
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
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(toDouble(a), toDouble(b));
        }
        if (a instanceof String sa && b instanceof String sb) {
            try {
                LocalDate da = LocalDate.parse(sa);
                LocalDate db = LocalDate.parse(sb);
                return da.compareTo(db);
            } catch (Exception ignored) {}
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
