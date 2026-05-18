package com.jrules.ruleengine.v2.parser.ast;

import java.util.List;

/**
 * All AST node types for the V2 expression language.
 * Kept in one file for readability — each is a simple data container.
 */
public final class Nodes {

    private Nodes() {}

    // ── Boolean ──────────────────────────────────────────────────────────────

    public static class BinaryOpNode extends ExpressionNode {
        public String operator;   // AND, OR, +, -, *, /, =, >=, etc.
        public ExpressionNode left;
        public ExpressionNode right;
    }

    public static class UnaryOpNode extends ExpressionNode {
        public String operator;   // NOT, unary minus (-)
        public ExpressionNode operand;
    }

    public static class TernaryNode extends ExpressionNode {
        public ExpressionNode condition;
        public ExpressionNode thenExpr;
        public ExpressionNode elseExpr;
    }

    // ── Comparisons ───────────────────────────────────────────────────────────

    public static class BetweenNode extends ExpressionNode {
        public ExpressionNode value;
        public ExpressionNode low;
        public ExpressionNode high;
        public boolean negated;   // NOT BETWEEN
    }

    public static class InNode extends ExpressionNode {
        public ExpressionNode value;
        public ExpressionNode listOrLookup;  // ListLiteralNode or LookupRefNode
        public boolean negated;              // NOT IN
    }

    public static class IsNullNode extends ExpressionNode {
        public ExpressionNode value;
        public boolean negated;   // IS NOT NULL
    }

    public static class StringOpNode extends ExpressionNode {
        public ExpressionNode value;
        public String operator;   // CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES
        public ExpressionNode pattern;
    }

    // ── Function call ─────────────────────────────────────────────────────────

    public static class FunctionCallNode extends ExpressionNode {
        public String name;
        public List<ExpressionNode> arguments;
    }

    // ── Primitives ────────────────────────────────────────────────────────────

    public static class ContextPathNode extends ExpressionNode {
        public List<String> segments;   // ["applicant", "bureau", "score"]
    }

    public static class LookupRefNode extends ExpressionNode {
        public String name;             // @approved_cities → "approved_cities"
    }

    public static class ListLiteralNode extends ExpressionNode {
        public List<ExpressionNode> elements;
    }

    public static class NumberLiteralNode extends ExpressionNode {
        public double value;
    }

    public static class StringLiteralNode extends ExpressionNode {
        public String value;
    }

    public static class BooleanLiteralNode extends ExpressionNode {
        public boolean value;
    }

    public static class NullLiteralNode extends ExpressionNode {}

    // ── Let block ─────────────────────────────────────────────────────────────
    // Syntax: let name = expr; name = expr; bodyExpr
    // Semantics: bindings are evaluated in order and added to a scoped variable
    // frame; the frame shadows the request context for single-segment names.

    public static class LetBlockNode extends ExpressionNode {
        public List<LetBinding> bindings;
        public ExpressionNode   body;

        public static final class LetBinding {
            public final String         name;
            public final ExpressionNode expression;

            public LetBinding(String name, ExpressionNode expression) {
                this.name       = name;
                this.expression = expression;
            }
        }
    }
}
