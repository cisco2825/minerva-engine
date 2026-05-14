package com.jrules.ruleengine.v2.function;

import com.jrules.ruleengine.v2.function.builtin.Builtins;
import com.jrules.ruleengine.v2.model.udf.UDF;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DefaultFunctionRegistry implements FunctionRegistry {

    private final Map<String, BuiltinFunction> builtins;

    public DefaultFunctionRegistry() {
        List<BuiltinFunction> all = List.of(
            new Builtins.AbsFunction(),
            new Builtins.RoundFunction(),
            new Builtins.FloorFunction(),
            new Builtins.CeilFunction(),
            new Builtins.MinFunction(),
            new Builtins.MaxFunction(),
            new Builtins.PowFunction(),
            new Builtins.SqrtFunction(),
            new Builtins.UpperFunction(),
            new Builtins.LowerFunction(),
            new Builtins.TrimFunction(),
            new Builtins.LengthFunction(),
            new Builtins.ConcatFunction(),
            new Builtins.SubstrFunction(),
            new Builtins.ReplaceFunction(),
            new Builtins.TodayFunction(),
            new Builtins.DateFunction(),
            new Builtins.DatediffFunction(),
            new Builtins.DateAddFunction(),
            new Builtins.IfFunction(),
            new Builtins.CoalesceFunction(),
            new Builtins.NullIfFunction(),
            new Builtins.ToNumberFunction(),
            new Builtins.ToStringFunction()
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

    @Override
    public boolean isKnown(String name) {
        return builtins.containsKey(name.toUpperCase()) || "TABLE".equalsIgnoreCase(name);
    }

    @Override
    public List<String> allKnownNames() {
        return Stream.concat(builtins.keySet().stream(), Stream.of("TABLE"))
                .sorted()
                .collect(Collectors.toList());
    }
}
