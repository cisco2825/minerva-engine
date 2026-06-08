package com.jrules.ruleengine.v2.function;

import com.jrules.ruleengine.v2.function.builtin.Builtins;
import com.jrules.ruleengine.v2.model.udf.UDF;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DefaultFunctionRegistry implements FunctionRegistry {

    private final Map<String, BuiltinFunction> builtins;

    public DefaultFunctionRegistry() {
        List<BuiltinFunction> all = List.of(
            // Math
            new Builtins.AbsFunction(),
            new Builtins.RoundFunction(),
            new Builtins.FloorFunction(),
            new Builtins.CeilFunction(),
            new Builtins.MinFunction(),
            new Builtins.MaxFunction(),
            new Builtins.PowFunction(),
            new Builtins.SqrtFunction(),
            // String
            new Builtins.UpperFunction(),
            new Builtins.LowerFunction(),
            new Builtins.TrimFunction(),
            new Builtins.LengthFunction(),
            new Builtins.ConcatFunction(),
            new Builtins.SubstrFunction(),
            new Builtins.ReplaceFunction(),
            new Builtins.SplitFunction(),
            new Builtins.JoinFunction(),
            // Date
            new Builtins.TodayFunction(),
            new Builtins.DateFunction(),
            new Builtins.DatediffFunction(),
            new Builtins.DateAddFunction(),
            new Builtins.AgeFunction(),
            new Builtins.YearFunction(),
            new Builtins.MonthFunction(),
            new Builtins.DayFunction(),
            // Logic
            new Builtins.IfFunction(),
            new Builtins.CoalesceFunction(),
            new Builtins.NullIfFunction(),
            // Type conversion
            new Builtins.ToNumberFunction(),
            new Builtins.ToStringFunction()
            // NOTE: ISNULL, ISNUMBER, ISSTRING are handled as special cases in
            // DefaultExpressionEvaluator (they need lazy argument evaluation) and
            // are registered below via SPECIAL_CASES for isKnown() / allKnownNames().
        );
        builtins = all.stream().collect(Collectors.toMap(f -> f.name().toUpperCase(), Function.identity()));
    }

    @Override
    public Optional<BuiltinFunction> getBuiltin(String name) {
        return Optional.ofNullable(builtins.get(name.toUpperCase()));
    }

    @Override
    public Optional<UDF> getUDF(String name) {
        // UDFs are per-request; looked up from EvaluationRequest by the evaluator
        return Optional.empty();
    }

    /**
     * Functions handled as special cases in the evaluator (lazy arg evaluation).
     * They are not in the {@code builtins} map but are still valid function names.
     */
    private static final Set<String> SPECIAL_CASES = Set.of("TABLE", "ISNULL", "ISNUMBER", "ISSTRING", "IFELSE", "LOOKUP");

    @Override
    public boolean isKnown(String name) {
        return builtins.containsKey(name.toUpperCase())
                || SPECIAL_CASES.contains(name.toUpperCase());
    }

    @Override
    public List<String> allKnownNames() {
        return Stream.concat(builtins.keySet().stream(), SPECIAL_CASES.stream())
                .sorted()
                .collect(Collectors.toList());
    }
}
