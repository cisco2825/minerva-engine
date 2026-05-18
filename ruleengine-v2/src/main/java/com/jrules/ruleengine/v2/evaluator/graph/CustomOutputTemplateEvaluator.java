package com.jrules.ruleengine.v2.evaluator.graph;

import com.jrules.ruleengine.v2.evaluator.expression.ExpressionEvaluator;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.parser.ExpressionParser;
import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses and evaluates a custom output template.
 *
 * Template format: JSON-like structure where:
 * - Keys must be double-quoted strings
 * - Values that are quoted strings / numbers / booleans / null are literals
 * - Values that are unquoted are expression strings evaluated against the request context
 *
 * Example:
 * <pre>
 * [
 *   { "bank": "AU SFB", "decision": IFELSE(workflows['AU_SFB v1.0'].outcome == "approved", "approved", "rejected") },
 *   { "bank": "IDFC",   "decision": workflows['IDFC v2.0'].outcome }
 * ]
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class CustomOutputTemplateEvaluator {

    private final ExpressionParser    expressionParser;
    private final ExpressionEvaluator expressionEvaluator;

    public Object evaluate(String template, EvaluationRequest request) {
        if (template == null || template.isBlank()) return null;
        int[] pos = {0};
        Object result = parseValue(template, pos, request);
        // ensure we consumed everything meaningful
        skipWs(template, pos);
        if (pos[0] < template.length()) {
            throw new EvaluationException(
                    "Custom output template has unexpected trailing content at position " + pos[0]);
        }
        return result;
    }

    // ── Recursive value parser ────────────────────────────────────────────────

    private Object parseValue(String t, int[] p, EvaluationRequest req) {
        skipWs(t, p);
        if (p[0] >= t.length()) return null;
        char c = t.charAt(p[0]);
        if (c == '{') return parseObject(t, p, req);
        if (c == '[') return parseArray(t, p, req);
        if (c == '"') return parseJsonString(t, p);
        if (c == '-' || Character.isDigit(c)) return parseJsonNumber(t, p);
        if (startsWith(t, p[0], "true"))  { p[0] += 4; return Boolean.TRUE;  }
        if (startsWith(t, p[0], "false")) { p[0] += 5; return Boolean.FALSE; }
        if (startsWith(t, p[0], "null"))  { p[0] += 4; return null; }
        return parseExpression(t, p, req);
    }

    private Map<String, Object> parseObject(String t, int[] p, EvaluationRequest req) {
        p[0]++; // consume '{'
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs(t, p);
        while (p[0] < t.length() && t.charAt(p[0]) != '}') {
            skipWs(t, p);
            if (t.charAt(p[0]) != '"') {
                throw new EvaluationException(
                        "Expected '\"' for object key at position " + p[0] + " in custom output template");
            }
            String key = parseJsonString(t, p);
            skipWs(t, p);
            if (p[0] >= t.length() || t.charAt(p[0]) != ':') {
                throw new EvaluationException("Expected ':' after key '" + key + "' in custom output template");
            }
            p[0]++; // ':'
            Object value = parseValue(t, p, req);
            map.put(key, value);
            skipWs(t, p);
            if (p[0] < t.length() && t.charAt(p[0]) == ',') { p[0]++; }
        }
        if (p[0] < t.length()) p[0]++; // consume '}'
        return map;
    }

    private List<Object> parseArray(String t, int[] p, EvaluationRequest req) {
        p[0]++; // consume '['
        List<Object> list = new ArrayList<>();
        skipWs(t, p);
        while (p[0] < t.length() && t.charAt(p[0]) != ']') {
            list.add(parseValue(t, p, req));
            skipWs(t, p);
            if (p[0] < t.length() && t.charAt(p[0]) == ',') { p[0]++; }
        }
        if (p[0] < t.length()) p[0]++; // consume ']'
        return list;
    }

    /** Parse a JSON double-quoted string (handles backslash escapes). */
    private String parseJsonString(String t, int[] p) {
        p[0]++; // skip opening '"'
        StringBuilder sb = new StringBuilder();
        while (p[0] < t.length() && t.charAt(p[0]) != '"') {
            char c = t.charAt(p[0]);
            if (c == '\\' && p[0] + 1 < t.length()) {
                p[0]++;
                char esc = t.charAt(p[0]);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> { sb.append('\\'); sb.append(esc); }
                }
            } else {
                sb.append(c);
            }
            p[0]++;
        }
        if (p[0] >= t.length()) {
            throw new EvaluationException("Unterminated string in custom output template");
        }
        p[0]++; // skip closing '"'
        return sb.toString();
    }

    private Number parseJsonNumber(String t, int[] p) {
        int start = p[0];
        if (t.charAt(p[0]) == '-') p[0]++;
        while (p[0] < t.length() && (Character.isDigit(t.charAt(p[0])) || t.charAt(p[0]) == '.')) p[0]++;
        String s = t.substring(start, p[0]);
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new EvaluationException("Invalid number in custom output template: " + s);
        }
    }

    /**
     * Scan an expression value — everything up to the next unmatched {@code ,}, {@code }}, or {@code ]}.
     * Tracks nesting depth for {@code ()}, {@code []}, {@code {}}, and skips over string literals
     * (both single- and double-quoted) to avoid false matches inside them.
     */
    private Object parseExpression(String t, int[] p, EvaluationRequest req) {
        int start = p[0];
        int depth = 0;
        boolean inDouble = false, inSingle = false;

        while (p[0] < t.length()) {
            char c = t.charAt(p[0]);
            if (inDouble) {
                if (c == '\\' && p[0] + 1 < t.length()) { p[0] += 2; continue; }
                if (c == '"') inDouble = false;
                p[0]++; continue;
            }
            if (inSingle) {
                if (c == '\\' && p[0] + 1 < t.length()) { p[0] += 2; continue; }
                if (c == '\'') inSingle = false;
                p[0]++; continue;
            }
            if (c == '"')  { inDouble = true;  p[0]++; continue; }
            if (c == '\'') { inSingle = true;  p[0]++; continue; }
            if (c == '(' || c == '[' || c == '{') { depth++; p[0]++; continue; }
            if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) break;
                depth--; p[0]++; continue;
            }
            if (c == ',' && depth == 0) break;
            p[0]++;
        }

        String exprText = t.substring(start, p[0]).trim();
        if (exprText.isEmpty()) return null;
        try {
            ExpressionNode ast = expressionParser.parse(exprText);
            return expressionEvaluator.evaluate(ast, req);
        } catch (Exception e) {
            throw new EvaluationException(
                    "Custom output template expression error in '" + exprText + "': " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void skipWs(String t, int[] p) {
        while (p[0] < t.length() && Character.isWhitespace(t.charAt(p[0]))) p[0]++;
    }

    private boolean startsWith(String t, int pos, String prefix) {
        if (pos + prefix.length() > t.length()) return false;
        if (!t.regionMatches(true, pos, prefix, 0, prefix.length())) return false;
        // Make sure it's not a longer identifier (e.g. "trueValue" should not match "true")
        int end = pos + prefix.length();
        if (end < t.length()) {
            char next = t.charAt(end);
            if (Character.isLetterOrDigit(next) || next == '_') return false;
        }
        return true;
    }
}
