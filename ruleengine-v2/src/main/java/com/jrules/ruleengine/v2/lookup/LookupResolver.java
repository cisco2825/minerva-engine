package com.jrules.ruleengine.v2.lookup;

import com.jrules.ruleengine.v2.model.lookup.Lookup;

import java.util.List;
import java.util.Map;

/**
 * Resolves @name references in expressions to their value lists.
 * INLINE lookups return values directly.
 * FILE lookups fetch from S3 via V1's S3DataSource (with Redis caching).
 */
public interface LookupResolver {

    List<Object> resolve(String name, Map<String, Lookup> lookups);
}
