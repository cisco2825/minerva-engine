package com.jrules.ruleengine.datasource.config;

import com.jrules.ruleengine.enums.FileFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for S3 datasource.
 *
 * @author Shubham Thakur
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class S3Config extends DataSourceSpecificConfig {
    private String bucket;
    private String path;
    private FileFormat format;
    private String extractKey;
}