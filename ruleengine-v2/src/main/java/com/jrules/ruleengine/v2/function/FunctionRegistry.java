package com.jrules.ruleengine.v2.function;

import com.jrules.ruleengine.v2.model.udf.UDF;

import java.util.List;
import java.util.Optional;

/**
 * Resolves function names to implementations at evaluation time.
 * Resolution order: built-ins first, then UDFs from the request.
 */
public interface FunctionRegistry {

    Optional<BuiltinFunction> getBuiltin(String name);

    Optional<UDF> getUDF(String name);

    boolean isKnown(String name);

    List<String> allKnownNames();
}
