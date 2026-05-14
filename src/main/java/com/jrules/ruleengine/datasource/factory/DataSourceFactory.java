package com.jrules.ruleengine.datasource.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.cache.CacheServiceable;
import com.jrules.ruleengine.datasource.DataSource;
import com.jrules.ruleengine.datasource.DataSourceConfig;
import com.jrules.ruleengine.datasource.impl.InlineDataSource;
import com.jrules.ruleengine.datasource.impl.S3DataSource;
import com.jrules.ruleengine.datasource.config.S3Config;
import com.jrules.ruleengine.enums.DataSourceType;
import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory for creating DataSource instances based on configuration.
 * 
 * <p>Supports multiple data source types:
 * <ul>
 *   <li>INLINE - Direct values embedded in configuration</li>
 *   <li>S3 - Values stored in S3 buckets (CSV/JSON formats)</li>
 * </ul>
 *
 * @author Shubham Thakur
 */
@Component
@RequiredArgsConstructor
public class DataSourceFactory {
    
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final CacheServiceable cacheServiceable;
    
    /**
     * Creates a DataSource instance based on the provided configuration.
     * 
     * @param config the data source configuration
     * @return DataSource instance (InlineDataSource or S3DataSource)
     * @throws IllegalArgumentException if data source type is unsupported
     */
    public DataSource createDataSource(DataSourceConfig config) {
        DataSourceType type = config.getType();
        
        if (type == null || type == DataSourceType.INLINE) {
            return new InlineDataSource(config.getValue());
        }
        
        if (type == DataSourceType.S3) {
            return createS3DataSource(config);
        }
        
        throw new IllegalArgumentException("Unsupported datasource type: " + type);
    }
    
    /**
     * Creates an S3DataSource with caching support.
     * 
     * @param config the data source configuration containing S3 details
     * @return S3DataSource instance configured with bucket, path, format, and extract key
     */
    private DataSource createS3DataSource(DataSourceConfig config) {
        S3Config s3Config = (S3Config) config.getConfig();
        return new S3DataSource(s3Config.getBucket(), s3Config.getPath(),
                               s3Config.getFormat(), s3Config.getExtractKey(),
                                DataType.from(config.getDatatype()), objectMapper, s3Service, cacheServiceable);
    }
}
