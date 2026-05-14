package com.jrules.ruleengine.v2.function;

import java.util.List;

/**
 * Contract for all built-in functions (ABS, TODAY, DATEDIFF, IF, TABLE, etc.).
 * Each implementation is registered in FunctionRegistry by name.
 */
public interface BuiltinFunction {

    String name();

    Object invoke(List<Object> args);
}
