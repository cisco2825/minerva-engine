package com.jrules.ruleengine.v2.validator;

import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.model.result.ValidationResult;
import com.jrules.ruleengine.v2.validator.stage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Orchestrates all seven validation stages in order.
 * All errors are collected before returning — never stops at first failure.
 * Skips later stages only when an earlier stage makes them meaningless
 * (e.g. if the policy is null, expression validation is skipped).
 */
@Component
@RequiredArgsConstructor
public class DefaultRequestValidator implements RequestValidator {

    private final Stage0RequestValidator  stage0;
    private final Stage1UdfValidator      stage1;
    private final Stage2LookupValidator   stage2;
    private final Stage3TableValidator    stage3;
    private final Stage4PolicyValidator   stage4;
    private final Stage5RuleValidator     stage5;
    private final Stage6ExpressionValidator stage6;

    @Override
    public ValidationResult validate(ValidationRequest request) {
        ValidationContext ctx = new ValidationContext(request);

        stage0.validate(ctx);

        // If the policy is fundamentally broken, later stages can't safely proceed
        if (request == null || request.getPolicy() == null || request.getPolicy().getType() == null) {
            return ctx.toResult();
        }

        stage1.validate(ctx);
        stage2.validate(ctx);
        stage3.validate(ctx);
        stage4.validate(ctx);
        stage5.validate(ctx);
        stage6.validate(ctx);

        return ctx.toResult();
    }
}
