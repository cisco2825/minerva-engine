package com.jrules.ruleengine.service;

import com.jrules.ruleengine.datasource.DataSource;
import com.jrules.ruleengine.datasource.DataSourceConfig;
import com.jrules.ruleengine.datasource.factory.DataSourceFactory;
import com.jrules.ruleengine.model.RuleSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for resolving values from different data sources.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataSourceResolver {

  private final DataSourceFactory dataSourceFactory;

    /**
     * Resolves values for a criterion from its configured data source.
     * 
     * @param criterion The criterion to resolve values for
     * @return List of resolved values
     */
    public List<Object> resolveValues(RuleSet.Criterion criterion) {
        DataSourceConfig config = criterion.getDatasource();
        DataSource dataSource = dataSourceFactory.createDataSource(config);
        return dataSource.resolveValues();
    }
}