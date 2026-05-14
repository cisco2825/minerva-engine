package com.jrules.ruleengine.datasource.impl;

import com.jrules.ruleengine.datasource.DataSource;
import lombok.RequiredArgsConstructor;
import java.util.List;

/**
 * DataSource implementation for inline values embedded directly in configuration.
 * 
 * <p>Returns values as-is without any external data fetching or transformation.
 * Used when values are small and can be included directly in the rule definition.
 *
 * @author Shubham Thakur
 */
@RequiredArgsConstructor
public class InlineDataSource implements DataSource {
    
    private final List<Object> values;
    
    /**
     * Returns the inline values without any processing.
     * 
     * @return list of inline values
     */
    @Override
    public List<Object> resolveValues() {
        return values;
    }
}