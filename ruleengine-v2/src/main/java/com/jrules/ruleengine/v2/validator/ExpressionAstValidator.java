package com.jrules.ruleengine.v2.validator;

import com.jrules.ruleengine.v2.function.FunctionRegistry;
import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.model.result.ValidationResult.TokenPosition;
import com.jrules.ruleengine.v2.model.udf.UDF;
import com.jrules.ruleengine.v2.parser.ast.ExpressionNode;
import com.jrules.ruleengine.v2.parser.ast.Nodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Walks an already-parsed AST and collects semantic validation errors:
 *   - unknown function names
 *   - wrong argument counts on UDF calls
 *   - @lookup references not in the request
 *   - TABLE() calls referencing unknown tables
 *   - TABLE() first argument must be a string literal
 */
@Component
@RequiredArgsConstructor
public class ExpressionAstValidator {

    private final FunctionRegistry functionRegistry;

    public void validate(ExpressionNode ast, ValidationRequest request,
                         ValidationContext ctx, String location) {
        walk(ast, request, ctx, location);
    }

    private void walk(ExpressionNode node, ValidationRequest request,
                      ValidationContext ctx, String location) {
        if (node == null) return;

        if (node instanceof Nodes.FunctionCallNode n) {
            validateFunction(n, request, ctx, location);
            // Recurse into arguments regardless of whether the function was known
            n.arguments.forEach(a -> walk(a, request, ctx, location));
            return;
        }
        if (node instanceof Nodes.LookupRefNode n) {
            validateLookupRef(n, request, ctx, location);
            return;
        }

        // Recurse into all children
        recurse(node, request, ctx, location);
    }

    // ── Validators ────────────────────────────────────────────────────────────

    private void validateFunction(Nodes.FunctionCallNode node, ValidationRequest request,
                                   ValidationContext ctx, String location) {
        String name = node.name.toUpperCase();

        if ("TABLE".equals(name)) {
            validateTableCall(node, request, ctx, location);
            return;
        }

        if ("LOOKUP".equals(name)) {
            validateLookupCall(node, request, ctx, location);
            return;
        }

        // Built-in check
        if (functionRegistry.getBuiltin(name).isPresent()) {
            return; // built-in argument counts are flexible or enforced at runtime
        }

        // UDF check
        List<UDF> udfs = request.getUdfs();
        if (udfs != null) {
            for (UDF udf : udfs) {
                if (name.equalsIgnoreCase(udf.getName())) {
                    validateUdfArgCount(node, udf, ctx, location);
                    return;
                }
            }
        }

        // Unknown
        ctx.error("EXPR.UNKNOWN_FUNCTION", "STAGE_6_EXPRESSION", location,
                "Unknown function: '" + node.name + "'",
                "Known functions: " + functionRegistry.allKnownNames(),
                pos(node));
    }

    private void validateUdfArgCount(Nodes.FunctionCallNode node, UDF udf,
                                      ValidationContext ctx, String location) {
        int expected = udf.getParams() == null ? 0 : udf.getParams().size();
        int actual   = node.arguments.size();
        if (expected != actual) {
            ctx.error("EXPR.UDF_WRONG_ARG_COUNT", "STAGE_6_EXPRESSION", location,
                    "UDF '" + udf.getName() + "' expects " + expected
                            + " argument(s), but got " + actual,
                    null, pos(node));
        }
    }

    private void validateTableCall(Nodes.FunctionCallNode node, ValidationRequest request,
                                    ValidationContext ctx, String location) {
        if (node.arguments.isEmpty()) {
            ctx.error("EXPR.TABLE_MISSING_NAME", "STAGE_6_EXPRESSION", location,
                    "TABLE() requires at least one argument (the table name as a string literal)",
                    null, pos(node));
            return;
        }
        ExpressionNode firstArg = node.arguments.get(0);
        if (!(firstArg instanceof Nodes.StringLiteralNode nameNode)) {
            ctx.error("EXPR.TABLE_NAME_NOT_STRING", "STAGE_6_EXPRESSION", location,
                    "TABLE() first argument must be a string literal (table name)",
                    "Example: TABLE(\"loanTable\", applicant.income)",
                    pos(firstArg));
            return;
        }
        String tableName = nameNode.value;
        if (request.getTables() == null || !request.getTables().containsKey(tableName)) {
            ctx.error("EXPR.TABLE_UNKNOWN", "STAGE_6_EXPRESSION", location,
                    "TABLE() references unknown table: '" + tableName + "'",
                    "Declare it in the 'tables' map of the request",
                    pos(nameNode));
        }
    }

    private void validateLookupCall(Nodes.FunctionCallNode node, ValidationRequest request,
                                     ValidationContext ctx, String location) {
        if (node.arguments.size() != 2) {
            ctx.error("EXPR.LOOKUP_WRONG_ARGS", "STAGE_6_EXPRESSION", location,
                    "LOOKUP() requires exactly 2 arguments: LOOKUP(\"name\", \"column\"), got "
                            + node.arguments.size(),
                    "Example: LOOKUP(\"cc_bank_list\", \"city\")",
                    pos(node));
            return;
        }
        ExpressionNode nameArg   = node.arguments.get(0);
        ExpressionNode columnArg = node.arguments.get(1);

        if (!(nameArg instanceof Nodes.StringLiteralNode nameNode)) {
            ctx.error("EXPR.LOOKUP_NAME_NOT_STRING", "STAGE_6_EXPRESSION", location,
                    "LOOKUP() first argument must be a string literal (the lookup name)",
                    "Example: LOOKUP(\"cc_bank_list\", \"city\")",
                    pos(nameArg));
            return;
        }
        if (!(columnArg instanceof Nodes.StringLiteralNode)) {
            ctx.error("EXPR.LOOKUP_COLUMN_NOT_STRING", "STAGE_6_EXPRESSION", location,
                    "LOOKUP() second argument must be a string literal (the column name)",
                    "Example: LOOKUP(\"cc_bank_list\", \"city\")",
                    pos(columnArg));
            return;
        }

        // If the validation request has lookups declared, verify this one is present
        String lookupName = nameNode.value;
        if (request.getLookups() != null && !request.getLookups().containsKey(lookupName)) {
            ctx.error("EXPR.LOOKUP_UNKNOWN", "STAGE_6_EXPRESSION", location,
                    "LOOKUP() references undeclared lookup: '" + lookupName + "'",
                    "Add it to a SOURCE node so it is loaded before this expression runs",
                    pos(nameNode));
        }
    }

    private void validateLookupRef(Nodes.LookupRefNode node, ValidationRequest request,
                                    ValidationContext ctx, String location) {
        if (request.getLookups() == null || !request.getLookups().containsKey(node.name)) {
            ctx.error("EXPR.UNKNOWN_LOOKUP", "STAGE_6_EXPRESSION", location,
                    "Lookup '@" + node.name + "' is not defined in the request",
                    "Add it to the 'lookups' map",
                    pos(node));
        }
    }

    // ── Recursive child traversal ─────────────────────────────────────────────

    private void recurse(ExpressionNode node, ValidationRequest request,
                          ValidationContext ctx, String location) {
        if (node instanceof Nodes.BinaryOpNode  n) { walk(n.left, request, ctx, location); walk(n.right, request, ctx, location); }
        else if (node instanceof Nodes.UnaryOpNode  n) { walk(n.operand, request, ctx, location); }
        else if (node instanceof Nodes.TernaryNode  n) { walk(n.condition, request, ctx, location); walk(n.thenExpr, request, ctx, location); walk(n.elseExpr, request, ctx, location); }
        else if (node instanceof Nodes.BetweenNode  n) { walk(n.value, request, ctx, location); walk(n.low, request, ctx, location); walk(n.high, request, ctx, location); }
        else if (node instanceof Nodes.InNode       n) { walk(n.value, request, ctx, location); walk(n.listOrLookup, request, ctx, location); }
        else if (node instanceof Nodes.IsNullNode   n) { walk(n.value, request, ctx, location); }
        else if (node instanceof Nodes.StringOpNode n) { walk(n.value, request, ctx, location); walk(n.pattern, request, ctx, location); }
        else if (node instanceof Nodes.ListLiteralNode n) { n.elements.forEach(e -> walk(e, request, ctx, location)); }
        // Leaf nodes (literals, context paths, lookup refs) — nothing to recurse into
    }

    private TokenPosition pos(ExpressionNode node) {
        return TokenPosition.builder()
                .line(node.line)
                .column(node.column)
                .length(node.length)
                .build();
    }
}
