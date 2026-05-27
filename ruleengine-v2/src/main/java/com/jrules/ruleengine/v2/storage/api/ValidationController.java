package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.evaluator.graph.CustomOutputTemplateEvaluator;
import com.jrules.ruleengine.v2.parser.ExpressionParser;
import com.jrules.ruleengine.v2.parser.ParseException;
import com.jrules.ruleengine.v2.parser.lexer.LexerException;
import com.jrules.ruleengine.v2.storage.dto.ValidateExpressionsRequest;
import com.jrules.ruleengine.v2.storage.dto.ValidateExpressionsResponse;
import com.jrules.ruleengine.v2.storage.dto.ValidateExpressionsResponse.ValidationError;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v2/validate")
@RequiredArgsConstructor
public class ValidationController {

    private final ExpressionParser               expressionParser;
    private final CustomOutputTemplateEvaluator  templateEvaluator;

    /**
     * Validates a batch of expressions / templates without executing them.
     * Returns a list of parse errors keyed by the caller-supplied label.
     * Always returns HTTP 200 — the {@code valid} flag carries the pass/fail signal.
     */
    @PostMapping("/expressions")
    public ValidateExpressionsResponse validate(@RequestBody ValidateExpressionsRequest request) {
        List<ValidationError> errors = new ArrayList<>();

        if (request.getExpressions() == null) {
            return new ValidateExpressionsResponse(true, errors);
        }

        for (ValidateExpressionsRequest.ExpressionEntry entry : request.getExpressions()) {
            String label = entry.getLabel() != null ? entry.getLabel() : "unknown";

            if (entry.getTemplate() != null && !entry.getTemplate().isBlank()) {
                try {
                    templateEvaluator.parseTemplate(entry.getTemplate());
                } catch (Exception e) {
                    // Template errors don't have a single line/col — message is descriptive enough
                    errors.add(new ValidationError(label, e.getMessage(), null, null));
                }

            } else if (entry.getExpression() != null && !entry.getExpression().isBlank()) {
                try {
                    expressionParser.parse(entry.getExpression());
                } catch (ParseException e) {
                    errors.add(new ValidationError(label, e.getMessage(), e.getLine(), e.getColumn()));
                } catch (LexerException e) {
                    errors.add(new ValidationError(label, e.getMessage(), e.getLine(), e.getColumn()));
                } catch (Exception e) {
                    errors.add(new ValidationError(label, e.getMessage(), null, null));
                }
            }
        }

        return new ValidateExpressionsResponse(errors.isEmpty(), errors);
    }
}
