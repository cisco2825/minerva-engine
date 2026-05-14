package com.jrules.ruleengine.datasource;

import com.jrules.ruleengine.datasource.config.DataSourceSpecificConfig;
import com.jrules.ruleengine.enums.DataSourceType;
import lombok.Data;
import java.util.List;

/**
 * Configuration model for different data sources.
 *
 * @author Shubham Thakur
 */
@Data
public class DataSourceConfig {
    
    /**
     * Type of data source: INLINE, S3
     */
    private DataSourceType type;
    
    /**
     * Inline values
     */
    private List<Object> value;
    
    /**
     * Datasource-specific configuration
     */
    private DataSourceSpecificConfig config;
    
    /**
     * Data type of the values in this datasource
     */
    private String datatype;
}