package com.jrules.ruleengine.datasource.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for datasource-specific configurations.
 *
 * @author Shubham Thakur
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(value = S3Config.class)
})
public abstract class DataSourceSpecificConfig {
}