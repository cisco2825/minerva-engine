package com.jrules.ruleengine.v2.lookup;

import com.jrules.ruleengine.v2.model.lookup.Lookup;

import java.util.List;
import java.util.Map;

/**
 * Resolves lookup references in expressions to their value lists.
 *
 * <p>Two resolution modes:
 * <ul>
 *   <li>{@link #resolve} — legacy {@code @name} syntax; uses the {@code extractKey}
 *       baked into the stored {@link com.jrules.ruleengine.v2.model.lookup.FileLookup}
 *       (defaults to column 0 when not set).</li>
 *   <li>{@link #resolveColumn} — new {@code LOOKUP("name", "column")} function; the
 *       column is supplied by the expression at call time, so one CSV can serve many
 *       different column lookups.</li>
 * </ul>
 */
public interface LookupResolver {

    /** Legacy: resolve {@code @name} — column determined by {@code FileLookup.extractKey}. */
    List<Object> resolve(String name, Map<String, Lookup> lookups);

    /**
     * New: resolve {@code LOOKUP("name", "column")} — column supplied by the caller.
     * For INLINE lookups the {@code column} argument is ignored (they are flat lists).
     */
    List<Object> resolveColumn(String name, String column, Map<String, Lookup> lookups);
}
