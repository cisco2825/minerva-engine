package com.jrules.ruleengine.v2.validator.stage;

import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.model.result.ValidationResult.TokenPosition;
import com.jrules.ruleengine.v2.model.rule.Rule;
import com.jrules.ruleengine.v2.model.udf.UDF;
import com.jrules.ruleengine.v2.parser.ExpressionParser;
import com.jrules.ruleengine.v2.parser.ParseException;
import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;
import com.jrules.ruleengine.v2.parser.lexer.LexerException;
import com.jrules.ruleengine.v2.validator.ExpressionAstValidator;
import com.jrules.ruleengine.v2.validator.ValidationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage 6 — parses every expression in the request (rules, UDF bodies, scorecard
 * variable params are context paths rather than expressions so we skip them here),
 * then runs semantic validation via ExpressionAstValidator.
 */
@Component
@RequiredArgsConstructor
public class Stage6ExpressionValidator {

    private static final String STAGE = "STAGE_6_EXPRESSION";

    private final ExpressionParser expressionParser;
    private final ExpressionAstValidator astValidator;

    public void validate(ValidationContext ctx) {
        ValidationRequest req = ctx.getRequest();
        if (req.getPolicy() == null || req.getPolicy().getType() == null) return;

        PolicyType type = req.getPolicy().getType();
        Policy policy = req.getPolicy();

        switch (type) {
            case RULE_CHAIN -> validateRuleExpressions(policy.getRules(), req, ctx);
            case DECISION_TABLE -> { /* Table cell conditions are literal values, not expressions */ }
            case SCORECARD -> { /* Scorecard variable params are context paths, not expressions */ }
        }

        // UDF expression bodies are always validated regardless of policy type
        validateUdfExpressions(req, ctx);
    }

    // ── Rule expressions ──────────────────────────────────────────────────────

    private void validateRuleExpressions(List<Rule> rules, ValidationRequest req, ValidationContext ctx) {
        if (rules == null) return;
        for (Rule rule : rules) {
            if (rule.getExpression() == null || rule.getExpression().isBlank()) continue;
            String loc = "policy.rules[" + rule.getName() + "].expression";
            parseAndValidate(rule.getExpression(), req, ctx, loc);
        }
    }

    // ── UDF expressions ───────────────────────────────────────────────────────

    private void validateUdfExpressions(ValidationRequest req, ValidationContext ctx) {
        List<UDF> udfs = req.getUdfs();
        if (udfs == null) return;
        for (UDF udf : udfs) {
            if (udf.getName() == null || udf.getExpression() == null || udf.getExpression().isBlank()) continue;
            String loc = "udfs[" + udf.getName() + "].expression";
            // Build a mini request with only the UDF params as context keys (no lookups/tables in UDF scope)
            ValidationRequest udfScope = buildUdfScope(udf, req);
            parseAndValidate(udf.getExpression(), udfScope, ctx, loc);
        }
    }

    // ── Core parse + validate ─────────────────────────────────────────────────

    private void parseAndValidate(String expression, ValidationRequest req,
                                   ValidationContext ctx, String location) {
        ExpressionNode ast;
        try {
            ast = expressionParser.parse(expression);
        } catch (LexerException e) {
            ctx.error("EXPR.LEX_ERROR", STAGE, location,
                    "Lexer error: " + rawMessage(e.getMessage()),
                    "Check for unsupported characters or unterminated strings",
                    TokenPosition.builder().line(e.getLine()).column(e.getColumn()).length(1).build());
            return;
        } catch (ParseException e) {
            ctx.error("EXPR.PARSE_ERROR", STAGE, location,
                    "Parse error: " + rawMessage(e.getMessage()),
                    "Check operator usage, parentheses, and keyword spelling",
                    TokenPosition.builder().line(e.getLine()).column(e.getColumn()).length(1).build());
            return;
        }
        // AST-level semantic checks
        astValidator.validate(ast, req, ctx, location);
    }

    /**
     * UDFs only have access to their own params — they cannot reference the main
     * context, other lookups, or tables. Build a restricted scope for validation.
     */
    private ValidationRequest buildUdfScope(UDF udf, ValidationRequest original) {
        ValidationRequest scope = new ValidationRequest();
        scope.setUdfs(original.getUdfs()); // UDFs can call other UDFs
        // No lookups or tables in UDF scope
        return scope;
    }

    /** Strip the position suffix added by LexerException/ParseException for a cleaner message body. */
    private String rawMessage(String fullMessage) {
        int at = fullMessage.lastIndexOf(" at ");
        return at > 0 ? fullMessage.substring(0, at) : fullMessage;
    }
}
