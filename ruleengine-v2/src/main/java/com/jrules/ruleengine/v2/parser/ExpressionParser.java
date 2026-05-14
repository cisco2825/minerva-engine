package com.jrules.ruleengine.v2.parser;

import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;
import com.jrules.ruleengine.v2.parser.ast.Nodes;
import com.jrules.ruleengine.v2.parser.lexer.Lexer;
import com.jrules.ruleengine.v2.parser.lexer.Token;
import com.jrules.ruleengine.v2.parser.lexer.TokenType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for V2 expression language.
 * Converts a token list (from the Lexer) into an AST.
 *
 * Grammar entry point: expression → ternaryExpr
 *
 * Operator precedence (lowest to highest):
 *   ternary, OR, AND, NOT, comparison, arithmetic (+/-), arithmetic (* / %),
 *   unary minus, function call, primary
 */
@Component
public class ExpressionParser {

    private List<Token> tokens;
    private int pos;

    public ExpressionNode parse(String expression) {
        this.tokens = new Lexer().tokenize(expression);
        this.pos = 0;
        ExpressionNode root = parseTernary();
        expect(TokenType.EOF);
        return root;
    }

    // ── Ternary: condition ? then : else ─────────────────────────────────────

    private ExpressionNode parseTernary() {
        ExpressionNode condition = parseOr();
        if (peek().is(TokenType.QUESTION)) {
            Token q = consume();
            ExpressionNode thenExpr = parseTernary();
            expect(TokenType.COLON);
            ExpressionNode elseExpr = parseTernary();
            Nodes.TernaryNode node = new Nodes.TernaryNode();
            node.condition = condition;
            node.thenExpr = thenExpr;
            node.elseExpr = elseExpr;
            node.line = q.getLine();
            node.column = q.getColumn();
            return node;
        }
        return condition;
    }

    // ── OR ────────────────────────────────────────────────────────────────────

    private ExpressionNode parseOr() {
        ExpressionNode left = parseAnd();
        while (peek().is(TokenType.OR)) {
            Token op = consume();
            ExpressionNode right = parseAnd();
            left = binary(left, "OR", right, op);
        }
        return left;
    }

    // ── AND ───────────────────────────────────────────────────────────────────

    private ExpressionNode parseAnd() {
        ExpressionNode left = parseNot();
        while (peek().is(TokenType.AND)) {
            Token op = consume();
            ExpressionNode right = parseNot();
            left = binary(left, "AND", right, op);
        }
        return left;
    }

    // ── NOT ───────────────────────────────────────────────────────────────────

    private ExpressionNode parseNot() {
        if (peek().is(TokenType.NOT)) {
            Token op = consume();
            ExpressionNode operand = parseNot();
            Nodes.UnaryOpNode node = new Nodes.UnaryOpNode();
            node.operator = "NOT";
            node.operand = operand;
            node.line = op.getLine();
            node.column = op.getColumn();
            return node;
        }
        return parseComparison();
    }

    // ── Comparison ────────────────────────────────────────────────────────────
    // Handles: =, !=, <, <=, >, >=, BETWEEN, NOT BETWEEN, IN, NOT IN,
    //          IS NULL, IS NOT NULL, CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES

    private ExpressionNode parseComparison() {
        ExpressionNode left = parseAddSub();

        Token t = peek();

        // BETWEEN / NOT BETWEEN
        if (t.is(TokenType.BETWEEN)) {
            Token op = consume();
            ExpressionNode low = parseAddSub();
            expect(TokenType.AND);
            ExpressionNode high = parseAddSub();
            Nodes.BetweenNode node = new Nodes.BetweenNode();
            node.value = left;
            node.low = low;
            node.high = high;
            node.negated = false;
            node.line = op.getLine();
            node.column = op.getColumn();
            return node;
        }

        // NOT BETWEEN / NOT IN
        if (t.is(TokenType.NOT)) {
            Token notToken = consume();
            Token next = peek();
            if (next.is(TokenType.BETWEEN)) {
                consume();
                ExpressionNode low = parseAddSub();
                expect(TokenType.AND);
                ExpressionNode high = parseAddSub();
                Nodes.BetweenNode node = new Nodes.BetweenNode();
                node.value = left;
                node.low = low;
                node.high = high;
                node.negated = true;
                node.line = notToken.getLine();
                node.column = notToken.getColumn();
                return node;
            }
            if (next.is(TokenType.IN)) {
                consume();
                ExpressionNode list = parseListOrLookup();
                Nodes.InNode node = new Nodes.InNode();
                node.value = left;
                node.listOrLookup = list;
                node.negated = true;
                node.line = notToken.getLine();
                node.column = notToken.getColumn();
                return node;
            }
            throw new ParseException("Expected BETWEEN or IN after NOT", notToken);
        }

        // IN
        if (t.is(TokenType.IN)) {
            Token op = consume();
            ExpressionNode list = parseListOrLookup();
            Nodes.InNode node = new Nodes.InNode();
            node.value = left;
            node.listOrLookup = list;
            node.negated = false;
            node.line = op.getLine();
            node.column = op.getColumn();
            return node;
        }

        // IS NULL / IS NOT NULL
        if (t.is(TokenType.IS)) {
            Token op = consume();
            boolean negated = false;
            if (peek().is(TokenType.NOT)) {
                consume();
                negated = true;
            }
            Token nullToken = peek();
            if (!nullToken.is(TokenType.NULL)) {
                throw new ParseException("Expected NULL after IS [NOT]", nullToken);
            }
            consume();
            Nodes.IsNullNode node = new Nodes.IsNullNode();
            node.value = left;
            node.negated = negated;
            node.line = op.getLine();
            node.column = op.getColumn();
            return node;
        }

        // String operations: CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES
        if (t.is(TokenType.CONTAINS) || t.is(TokenType.STARTS_WITH)
                || t.is(TokenType.ENDS_WITH) || t.is(TokenType.MATCHES)) {
            Token op = consume();
            ExpressionNode pattern = parseAddSub();
            Nodes.StringOpNode node = new Nodes.StringOpNode();
            node.value = left;
            node.operator = op.getType().name();
            node.pattern = pattern;
            node.line = op.getLine();
            node.column = op.getColumn();
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

    // ── Additive: +, - ────────────────────────────────────────────────────────

    private ExpressionNode parseAddSub() {
        ExpressionNode left = parseMulDiv();
        while (peek().is(TokenType.PLUS) || peek().is(TokenType.MINUS)) {
            Token op = consume();
            ExpressionNode right = parseMulDiv();
            left = binary(left, op.getValue(), right, op);
        }
        return left;
    }

    // ── Multiplicative: *, /, % ───────────────────────────────────────────────

    private ExpressionNode parseMulDiv() {
        ExpressionNode left = parseUnary();
        while (peek().is(TokenType.STAR) || peek().is(TokenType.SLASH) || peek().is(TokenType.PERCENT)) {
            Token op = consume();
            ExpressionNode right = parseUnary();
            left = binary(left, op.getValue(), right, op);
        }
        return left;
    }

    // ── Unary minus ───────────────────────────────────────────────────────────

    private ExpressionNode parseUnary() {
        if (peek().is(TokenType.MINUS)) {
            Token op = consume();
            ExpressionNode operand = parseUnary();
            Nodes.UnaryOpNode node = new Nodes.UnaryOpNode();
            node.operator = "-";
            node.operand = operand;
            node.line = op.getLine();
            node.column = op.getColumn();
            return node;
        }
        return parsePrimary();
    }

    // ── Primary ───────────────────────────────────────────────────────────────

    private ExpressionNode parsePrimary() {
        Token t = peek();

        // Grouped expression
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
            node.value = Double.parseDouble(t.getValue());
            node.line = t.getLine();
            node.column = t.getColumn();
            node.length = t.getValue().length();
            return node;
        }

        // String literal
        if (t.is(TokenType.STRING)) {
            consume();
            Nodes.StringLiteralNode node = new Nodes.StringLiteralNode();
            node.value = t.getValue();
            node.line = t.getLine();
            node.column = t.getColumn();
            node.length = t.getValue().length() + 2;
            return node;
        }

        // Boolean literal (TRUE/FALSE stored as BOOLEAN token)
        if (t.is(TokenType.BOOLEAN)) {
            consume();
            Nodes.BooleanLiteralNode node = new Nodes.BooleanLiteralNode();
            node.value = "true".equalsIgnoreCase(t.getValue());
            node.line = t.getLine();
            node.column = t.getColumn();
            node.length = t.getValue().length();
            return node;
        }

        // NULL literal
        if (t.is(TokenType.NULL)) {
            consume();
            Nodes.NullLiteralNode node = new Nodes.NullLiteralNode();
            node.line = t.getLine();
            node.column = t.getColumn();
            node.length = 4;
            return node;
        }

        // Lookup reference: @name
        if (t.is(TokenType.AT)) {
            consume();
            Nodes.LookupRefNode node = new Nodes.LookupRefNode();
            node.name = t.getValue();
            node.line = t.getLine();
            node.column = t.getColumn();
            node.length = t.getValue().length() + 1;
            return node;
        }

        // List literal: [a, b, c]
        if (t.is(TokenType.LBRACKET)) {
            return parseListLiteral();
        }

        // TABLE keyword followed by ( is a special built-in function call
        if (t.is(TokenType.TABLE)) {
            consume(); // consume TABLE
            return parseFunctionCall(t);
        }

        // Function call or context path: starts with IDENTIFIER
        if (t.is(TokenType.IDENTIFIER)) {
            return parseIdentifierOrCall();
        }

        throw new ParseException("Unexpected token: " + t, t);
    }

    // ── Context path or function call ─────────────────────────────────────────

    private ExpressionNode parseIdentifierOrCall() {
        Token first = consume(); // IDENTIFIER
        List<String> segments = new ArrayList<>();
        segments.add(first.getValue());

        // Dotted path: applicant.bureau.score
        while (peek().is(TokenType.DOT)) {
            consume(); // DOT
            Token segment = expect(TokenType.IDENTIFIER);
            segments.add(segment.getValue());
        }

        // Function call: name( args )
        if (segments.size() == 1 && peek().is(TokenType.LPAREN)) {
            return parseFunctionCall(first);
        }

        Nodes.ContextPathNode node = new Nodes.ContextPathNode();
        node.segments = segments;
        node.line = first.getLine();
        node.column = first.getColumn();
        node.length = first.getValue().length();
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
        node.name = nameToken.getValue().toUpperCase();
        node.arguments = args;
        node.line = nameToken.getLine();
        node.column = nameToken.getColumn();
        node.length = nameToken.getValue().length();
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
        node.line = bracket.getLine();
        node.column = bracket.getColumn();
        return node;
    }

    // IN can be followed by a list literal OR a lookup ref
    private ExpressionNode parseListOrLookup() {
        if (peek().is(TokenType.AT)) {
            Token t = consume();
            Nodes.LookupRefNode node = new Nodes.LookupRefNode();
            node.name = t.getValue();
            node.line = t.getLine();
            node.column = t.getColumn();
            return node;
        }
        return parseListLiteral();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Nodes.BinaryOpNode binary(ExpressionNode left, String op, ExpressionNode right, Token opToken) {
        Nodes.BinaryOpNode node = new Nodes.BinaryOpNode();
        node.left = left;
        node.operator = op;
        node.right = right;
        node.line = opToken.getLine();
        node.column = opToken.getColumn();
        return node;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token consume() {
        return tokens.get(pos++);
    }

    private Token expect(TokenType type) {
        Token t = peek();
        if (!t.is(type)) {
            throw new ParseException("Expected " + type + " but got " + t.getType(), t);
        }
        return consume();
    }
}
