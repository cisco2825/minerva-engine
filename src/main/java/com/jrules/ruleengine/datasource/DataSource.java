package com.jrules.ruleengine.datasource;

import java.util.List;

/**
 * Interface for resolving values from different data sources.
 *
 * @author Shubham Thakur
 */
public interface DataSource {
    
    /**
     * Resolves values from the configured data source.
     * 
     * @return List of values from the data source
     */
    List<Object> resolveValues();
}