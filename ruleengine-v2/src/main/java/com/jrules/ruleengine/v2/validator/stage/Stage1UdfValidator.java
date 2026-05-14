package com.jrules.ruleengine.v2.validator.stage;

import com.jrules.ruleengine.v2.function.FunctionRegistry;
import com.jrules.ruleengine.v2.model.udf.UDF;
import com.jrules.ruleengine.v2.model.udf.UDFParam;
import com.jrules.ruleengine.v2.parser.ExpressionParser;
import com.jrules.ruleengine.v2.parser.ParseException;
import com.jrules.ruleengine.v2.parser.lexer.LexerException;
import com.jrules.ruleengine.v2.validator.ValidationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class Stage1UdfValidator {

    private static final String STAGE = "STAGE_1_UDF";

    private final FunctionRegistry functionRegistry;
    private final ExpressionParser expressionParser;

    public void validate(ValidationContext ctx) {
        List<UDF> udfs = ctx.getRequest().getUdfs();
        if (udfs == null || udfs.isEmpty()) return;

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < udfs.size(); i++) {
            UDF udf = udfs.get(i);
            String loc = "udfs[" + i + "]";

            if (isBlank(udf.getName())) {
                ctx.error("UDF.MISSING_NAME", STAGE, loc, "UDF name must not be blank");
                continue;
            }
            loc = "udfs[" + udf.getName() + "]";

            // Duplicate name check
            if (!seen.add(udf.getName().toLowerCase())) {
                ctx.error("UDF.DUPLICATE_NAME", STAGE, loc,
                        "Duplicate UDF name: '" + udf.getName() + "'");
            }

            // Clash with built-in
            if (functionRegistry.isKnown(udf.getName())) {
                ctx.warning("UDF.SHADOWS_BUILTIN", STAGE, loc,
                        "UDF '" + udf.getName() + "' shadows a built-in function. Built-in takes precedence.");
            }

            // Params
            if (udf.getParams() != null) {
                Set<String> paramNames = new HashSet<>();
                for (int j = 0; j < udf.getParams().size(); j++) {
                    UDFParam param = udf.getParams().get(j);
                    String paramLoc = loc + ".params[" + j + "]";
                    if (isBlank(param.getName())) {
                        ctx.error("UDF.PARAM_MISSING_NAME", STAGE, paramLoc, "UDF param name must not be blank");
                    } else if (!paramNames.add(param.getName().toLowerCase())) {
                        ctx.error("UDF.PARAM_DUPLICATE_NAME", STAGE, paramLoc,
                                "Duplicate param name '" + param.getName() + "' in UDF '" + udf.getName() + "'");
                    }
                    if (param.getType() == null) {
                        ctx.error("UDF.PARAM_MISSING_TYPE", STAGE, paramLoc,
                                "UDF param '" + param.getName() + "' is missing a type");
                    }
                }
            }

            // Expression parse check
            if (isBlank(udf.getExpression())) {
                ctx.error("UDF.MISSING_EXPRESSION", STAGE, loc, "UDF expression must not be blank");
            } else {
                try {
                    expressionParser.parse(udf.getExpression());
                } catch (LexerException e) {
                    ctx.error("UDF.EXPR_LEX_ERROR", STAGE, loc,
                            "UDF '" + udf.getName() + "': lexer error — " + e.getMessage(),
                            null,
                            com.jrules.ruleengine.v2.model.result.ValidationResult.TokenPosition.builder()
                                    .line(e.getLine()).column(e.getColumn()).length(1).build());
                } catch (ParseException e) {
                    ctx.error("UDF.EXPR_PARSE_ERROR", STAGE, loc,
                            "UDF '" + udf.getName() + "': parse error — " + e.getMessage(),
                            null,
                            com.jrules.ruleengine.v2.model.result.ValidationResult.TokenPosition.builder()
                                    .line(e.getLine()).column(e.getColumn()).length(1).build());
                }
            }
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
