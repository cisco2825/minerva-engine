package com.jrules.ruleengine.v2.parser;

import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;
import com.jrules.ruleengine.v2.parser.ast.Nodes;
import com.jrules.ruleengine.v2.parser.lexer.Lexer;
import com.jrules.ruleengine.v2.parser.lexer.Token;
import com.jrules.ruleengine.v2.parser.lexer.TokenType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recursive-descent parser for the V2 expression language.
 *
 * <p><b>Thread safety:</b> This bean is a stateless {@code @Component} singleton.
 * All mutable parse state lives inside the private {@link ParseState} inner class,
 * which is stack-allocated once per {@link #parse(String)} call — no shared state.
 *
 * <p><b>AST cache:</b> Parsed ASTs are memoised in a {@link ConcurrentHashMap}.
 * {@link ExpressionNode} subtypes are write-once data bags; once an AST is built
 * it is never mutated, so sharing it across threads and requests is safe.
 *
 * <p><b>Grammar</b> (lowest to highest binding strength):
 * <pre>
 *   expression  → letBlock | ternary
 *   letBlock    → LET binding (';' binding)* ';' ternary
 *   binding     → IDENTIFIER '=' ternary
 *   ternary     → or ( '?' ternary ':' ternary )?
 *   or          → and  ( OR  and  )*
 *   and         → not  ( AND not  )*
 *   not         → NOT not | comparison
 *   comparison  → addSub ( relOp | BETWEEN | IN | IS [NOT] NULL | stringOp )?
 *   addSub      → mulDiv ( ('+' | '-') mulDiv )*
 *   mulDiv      → unary  ( ('*' | '/' | '%') unary )*
 *   unary       → '-' unary | primary
 *   primary     → NUMBER | STRING | BOOLEAN | NULL
 *               | '(' expression ')'
 *               | '@' name
 *               | '[' list ']'
 *               | TABLE '(' args ')'
 *               | IDENTIFIER ( '.' IDENTIFIER )* [ '(' args ')' ]
 * </pre>
 */
@Component
public class ExpressionParser {

    /**
     * Thread-safe AST cache keyed by raw expression string.
     * Eliminates redundant lexing + parsing for hot/repeated expressions.
     */
    private final ConcurrentHashMap<String, ExpressionNode> cache = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse {@code expression} into an AST, using the cache when possible.
     */
    public ExpressionNode parse(String expression) {
        return cache.computeIfAbsent(expression, this::doParse);
    }

    private ExpressionNode doParse(String expression) {
        List<Token> tokens = new Lexer().tokenize(expression);
        ParseState  state  = new ParseState(tokens);
        ExpressionNode root = state.parseExpression();
        state.expect(TokenType.EOF);
        return root;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ParseState — one instance per doParse() call; owns all mutable parse state.
    // ─────────────────────────────────────────────────────────────────────────

    private static final class ParseState {

        private final List<Token> tokens;
        private int pos;

        ParseState(List<Token> tokens) {
            this.tokens = tokens;
            this.pos    = 0;
        }

        // ── Entry point ──────────────────────────────────────────────────────

        ExpressionNode parseExpression() {
            if (peek().is(TokenType.LET)) {
                return parseLetBlock();
            }
            return parseTernary();
        }

        // ── Let block ────────────────────────────────────────────────────────
        // let x = <expr>; y = <expr>; <bodyExpr>
        //
        // Detection of "is this a binding vs the body" uses one token of look-
        // ahead beyond the SEMICOLON: if the next two tokens are IDENTIFIER '='
        // (single-char equals, not '=='), it is another binding; otherwise it is
        // the body expression.

        private ExpressionNode parseLetBlock() {
            Token letToken = consume(); // LET

            List<Nodes.LetBlockNode.LetBinding> bindings = new ArrayList<>();
            bindings.add(parseLetBinding());

            while (peek().is(TokenType.SEMICOLON)) {
                consume(); // SEMICOLON
                if (peek().is(TokenType.EOF) || peek().is(TokenType.RPAREN)) {
                    throw new ParseException(
                            "let block is missing its body expression after the last ';'", peek());
                }
                if (isLetBinding()) {
                    bindings.add(parseLetBinding());
                } else {
                    // Everything remaining is the body expression
                    ExpressionNode body = parseTernary();
                    Nodes.LetBlockNode node = new Nodes.LetBlockNode();
                    node.bindings = bindings;
                    node.body     = body;
                    node.line     = letToken.getLine();
                    node.column   = letToken.getColumn();
                    return node;
                }
            }

            throw new ParseException(
                    "let block must end with a body expression — separate bindings and body with ';'",
                    letToken);
        }

        /**
         * Returns true iff the current position looks like {@code IDENTIFIER '='}
         * where {@code '='} is a single assignment sign (not {@code '=='}).
         */
        private boolean isLetBinding() {
            return peekAt(0).is(TokenType.IDENTIFIER)
                    && peekAt(1).is(TokenType.EQ)
                    && "=".equals(peekAt(1).getValue());
        }

        private Nodes.LetBlockNode.LetBinding parseLetBinding() {
            Token name = expect(TokenType.IDENTIFIER);
            Token eq   = peek();
            if (!eq.is(TokenType.EQ) || !"=".equals(eq.getValue())) {
                throw new ParseException("Expected '=' in let binding, got: " + eq.getType(), eq);
            }
            consume(); // '='
            ExpressionNode expr = parseTernary();
            return new Nodes.LetBlockNode.LetBinding(name.getValue(), expr);
        }

        // ── Ternary ──────────────────────────────────────────────────────────

        private ExpressionNode parseTernary() {
            ExpressionNode condition = parseOr();
            if (peek().is(TokenType.QUESTION)) {
                Token q = consume();
                ExpressionNode thenExpr = parseTernary();
                expect(TokenType.COLON);
                ExpressionNode elseExpr = parseTernary();
                Nodes.TernaryNode node = new Nodes.TernaryNode();
                node.condition = condition;
                node.thenExpr  = thenExpr;
                node.elseExpr  = elseExpr;
                node.line      = q.getLine();
                node.column    = q.getColumn();
                return node;
            }
            return condition;
        }

        // ── OR ───────────────────────────────────────────────────────────────

        private ExpressionNode parseOr() {
            ExpressionNode left = parseAnd();
            while (peek().is(TokenType.OR)) {
                Token op    = consume();
                ExpressionNode right = parseAnd();
                left = binary(left, "OR", right, op);
            }
            return left;
        }

        // ── AND ──────────────────────────────────────────────────────────────

        private ExpressionNode parseAnd() {
            ExpressionNode left = parseNot();
            while (peek().is(TokenType.AND)) {
                Token op    = consume();
                ExpressionNode right = parseNot();
                left = binary(left, "AND", right, op);
            }
            return left;
        }

        // ── NOT ──────────────────────────────────────────────────────────────

        private ExpressionNode parseNot() {
            if (peek().is(TokenType.NOT)) {
                Token op = consume();
                ExpressionNode operand = parseNot();
                Nodes.UnaryOpNode node = new Nodes.UnaryOpNode();
                node.operator = "NOT";
                node.operand  = operand;
                node.line     = op.getLine();
                node.column   = op.getColumn();
                return node;
            }
            return parseComparison();
        }

        // ── Comparison ───────────────────────────────────────────────────────
        // =, ==, !=, <>, <, <=, >, >=, BETWEEN, NOT BETWEEN,
        // IN, NOT IN, IS NULL, IS NOT NULL,
        // CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES

        private ExpressionNode parseComparison() {
            ExpressionNode left = parseAddSub();
            Token t = peek();

            // BETWEEN
            if (t.is(TokenType.BETWEEN)) {
                Token op = consume();
                ExpressionNode low  = parseAddSub();
                expect(TokenType.AND);
                ExpressionNode high = parseAddSub();
                Nodes.BetweenNode node = new Nodes.BetweenNode();
                node.value = left; node.low = low; node.high = high;
                node.negated = false;
                node.line = op.getLine(); node.column = op.getColumn();
                return node;
            }

            // NOT BETWEEN / NOT IN
            if (t.is(TokenType.NOT)) {
                Token notToken = consume();
                Token next = peek();
                if (next.is(TokenType.BETWEEN)) {
                    consume();
                    ExpressionNode low  = parseAddSub();
                    expect(TokenType.AND);
                    ExpressionNode high = parseAddSub();
                    Nodes.BetweenNode node = new Nodes.BetweenNode();
                    node.value = left; node.low = low; node.high = high;
                    node.negated = true;
                    node.line = notToken.getLine(); node.column = notToken.getColumn();
                    return node;
                }
                if (next.is(TokenType.IN)) {
                    consume();
                    ExpressionNode list = parseListOrLookup();
                    Nodes.InNode node = new Nodes.InNode();
                    node.value = left; node.listOrLookup = list; node.negated = true;
                    node.line = notToken.getLine(); node.column = notToken.getColumn();
                    return node;
                }
                throw new ParseException("Expected BETWEEN or IN after NOT", notToken);
            }

            // IN
            if (t.is(TokenType.IN)) {
                Token op = consume();
                ExpressionNode list = parseListOrLookup();
                Nodes.InNode node = new Nodes.InNode();
                node.value = left; node.listOrLookup = list; node.negated = false;
                node.line = op.getLine(); node.column = op.getColumn();
                return node;
            }

            // IS NULL / IS NOT NULL
            if (t.is(TokenType.IS)) {
                Token op = consume();
                boolean negated = false;
                if (peek().is(TokenType.NOT)) { consume(); negated = true; }
                Token nullToken = peek();
                if (!nullToken.is(TokenType.NULL)) {
                    throw new ParseException("Expected NULL after IS [NOT]", nullToken);
                }
                consume();
                Nodes.IsNullNode node = new Nodes.IsNullNode();
                node.value = left; node.negated = negated;
                node.line = op.getLine(); node.column = op.getColumn();
                return node;
            }

            // String operations
            if (t.is(TokenType.CONTAINS) || t.is(TokenType.STARTS_WITH)
                    || t.is(TokenType.ENDS_WITH) || t.is(TokenType.MATCHES)) {
                Token op = consume();
                ExpressionNode pattern = parseAddSub();
                Nodes.StringOpNode node = new Nodes.StringOpNode();
                node.value = left; node.operator = op.getType().name(); node.pattern = pattern;
                node.line = op.getLine(); node.column = op.getColumn();
                return node;
            }

            // Equality / relational
            if (t.is(TokenType.EQ) || t.is(TokenType.NEQ) || t.is(TokenType.LT)
                    || t.is(TokenType.LTE) || t.is(TokenType.GT) || t.is(TokenType.GTE)) {
                Token op = consume();
                ExpressionNode right = parseAddSub();
                return binary(left, op.getValue(), right, op);
            }

            return left;
        }

        // ── Additive: +, - ───────────────────────────────────────────────────

        private ExpressionNode parseAddSub() {
            ExpressionNode left = parseMulDiv();
            while (peek().is(TokenType.PLUS) || peek().is(TokenType.MINUS)) {
                Token op = consume();
                ExpressionNode right = parseMulDiv();
                left = binary(left, op.getValue(), right, op);
            }
            return left;
        }

        // ── Multiplicative: *, /, % ──────────────────────────────────────────

        private ExpressionNode parseMulDiv() {
            ExpressionNode left = parseUnary();
            while (peek().is(TokenType.STAR) || peek().is(TokenType.SLASH)
                    || peek().is(TokenType.PERCENT)) {
                Token op = consume();
                ExpressionNode right = parseUnary();
                left = binary(left, op.getValue(), right, op);
            }
            return left;
        }

        // ── Unary minus ──────────────────────────────────────────────────────

        private ExpressionNode parseUnary() {
            if (peek().is(TokenType.MINUS)) {
                Token op = consume();
                ExpressionNode operand = parseUnary();
                Nodes.UnaryOpNode node = new Nodes.UnaryOpNode();
                node.operator = "-"; node.operand = operand;
                node.line = op.getLine(); node.column = op.getColumn();
                return node;
            }
            return parsePrimary();
        }

        // ── Primary ──────────────────────────────────────────────────────────

        private ExpressionNode parsePrimary() {
            Token t = peek();

            // Grouped sub-expression
            if (t.is(TokenType.LPAREN)) {
                consume();
                ExpressionNode inner = parseTernary();
                expect(TokenType.RPAREN);
                return inner;
            }

            // Number literal
            if (t.is(TokenType.NUMBER)) {
                consume();
                Nodes.NumberLiteralNode node = new Nodes.NumberLiteralNode();
                node.value  = Double.parseDouble(t.getValue());
                node.line   = t.getLine(); node.column = t.getColumn();
                node.length = t.getValue().length();
                return node;
            }

            // String literal
            if (t.is(TokenType.STRING)) {
                consume();
                Nodes.StringLiteralNode node = new Nodes.StringLiteralNode();
                node.value  = t.getValue();
                node.line   = t.getLine(); node.column = t.getColumn();
                node.length = t.getValue().length() + 2; // account for quotes
                return node;
            }

            // Boolean literal (TRUE/FALSE lexed as BOOLEAN)
            if (t.is(TokenType.BOOLEAN)) {
                consume();
                Nodes.BooleanLiteralNode node = new Nodes.BooleanLiteralNode();
                node.value  = "true".equalsIgnoreCase(t.getValue());
                node.line   = t.getLine(); node.column = t.getColumn();
                node.length = t.getValue().length();
                return node;
            }

            // NULL literal
            if (t.is(TokenType.NULL)) {
                consume();
                Nodes.NullLiteralNode node = new Nodes.NullLiteralNode();
                node.line = t.getLine(); node.column = t.getColumn(); node.length = 4;
                return node;
            }

            // Lookup reference: @name
            if (t.is(TokenType.AT)) {
                consume();
                Nodes.LookupRefNode node = new Nodes.LookupRefNode();
                node.name   = t.getValue();
                node.line   = t.getLine(); node.column = t.getColumn();
                node.length = t.getValue().length() + 1;
                return node;
            }

            // List literal: [a, b, c]
            if (t.is(TokenType.LBRACKET)) {
                return parseListLiteral();
            }

            // TABLE(name, arg1, ...) — built-in keyword function
            if (t.is(TokenType.TABLE)) {
                consume();
                return parseFunctionCall(t);
            }

            // Identifier: context path (a.b.c) or function call (f(args))
            if (t.is(TokenType.IDENTIFIER)) {
                return parseIdentifierOrCall();
            }

            throw new ParseException("Unexpected token: " + t.getType()
                    + " ('" + t.getValue() + "')", t);
        }

        private ExpressionNode parseIdentifierOrCall() {
            Token first = consume(); // IDENTIFIER
            List<String> segments = new ArrayList<>();
            segments.add(first.getValue());

            // Dotted path: applicant.bureau.score
            while (peek().is(TokenType.DOT)) {
                consume();
                Token seg = expect(TokenType.IDENTIFIER);
                segments.add(seg.getValue());
            }

            // Function call: name(args)
            if (segments.size() == 1 && peek().is(TokenType.LPAREN)) {
                return parseFunctionCall(first);
            }

            Nodes.ContextPathNode node = new Nodes.ContextPathNode();
            node.segments = segments;
            node.line     = first.getLine(); node.column = first.getColumn();
            node.length   = first.getValue().length();
            return node;
        }

        private ExpressionNode parseFunctionCall(Token nameToken) {
            expect(TokenType.LPAREN);
            List<ExpressionNode> args = new ArrayList<>();
            if (!peek().is(TokenType.RPAREN)) {
                args.add(parseTernary());
                while (peek().is(TokenType.COMMA)) {
                    consume();
                    args.add(parseTernary());
                }
            }
            expect(TokenType.RPAREN);
            Nodes.FunctionCallNode node = new Nodes.FunctionCallNode();
            node.name      = nameToken.getValue().toUpperCase();
            node.arguments = args;
            node.line      = nameToken.getLine(); node.column = nameToken.getColumn();
            node.length    = nameToken.getValue().length();
            return node;
        }

        private ExpressionNode parseListLiteral() {
            Token bracket = consume(); // LBRACKET
            List<ExpressionNode> elements = new ArrayList<>();
            if (!peek().is(TokenType.RBRACKET)) {
                elements.add(parseTernary());
                while (peek().is(TokenType.COMMA)) {
                    consume();
                    elements.add(parseTernary());
                }
            }
            expect(TokenType.RBRACKET);
            Nodes.ListLiteralNode node = new Nodes.ListLiteralNode();
            node.elements = elements;
            node.line = bracket.getLine(); node.column = bracket.getColumn();
            return node;
        }

        /**
         * Parse the right-hand side of {@code IN} / {@code NOT IN}.
         * Accepts:
         * <ul>
         *   <li>{@code @lookupRef} — a lookup table reference</li>
         *   <li>{@code [a, b, c]} — an inline list literal</li>
         *   <li>any primary expression that returns a {@code List} at runtime,
         *       e.g. {@code SPLIT(applicant.tags, ",")} </li>
         * </ul>
         */
        private ExpressionNode parseListOrLookup() {
            if (peek().is(TokenType.AT)) {
                Token t = consume();
                Nodes.LookupRefNode node = new Nodes.LookupRefNode();
                node.name   = t.getValue();
                node.line   = t.getLine(); node.column = t.getColumn();
                return node;
            }
            if (peek().is(TokenType.LBRACKET)) {
                return parseListLiteral();
            }
            // Function call or context path that yields a list at runtime
            return parsePrimary();
        }

        // ── Helpers ──────────────────────────────────────────────────────────

        private Nodes.BinaryOpNode binary(ExpressionNode left, String op,
                                          ExpressionNode right, Token opToken) {
            Nodes.BinaryOpNode node = new Nodes.BinaryOpNode();
            node.left     = left;
            node.operator = op;
            node.right    = right;
            node.line     = opToken.getLine();
            node.column   = opToken.getColumn();
            return node;
        }

        private Token peek() {
            return tokens.get(pos);
        }

        /** Look ahead by {@code offset} positions; returns EOF token if past end. */
        private Token peekAt(int offset) {
            int idx = pos + offset;
            return (idx < tokens.size()) ? tokens.get(idx) : tokens.get(tokens.size() - 1);
        }

        private Token consume() {
            return tokens.get(pos++);
        }

        private Token expect(TokenType type) {
            Token t = peek();
            if (!t.is(type)) {
                throw new ParseException(
                        "Expected " + type + " but got " + t.getType()
                                + " ('" + t.getValue() + "')", t);
            }
            return consume();
        }
    }
}
