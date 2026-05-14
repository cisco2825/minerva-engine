package com.jrules.ruleengine.validator.impl;

import com.jrules.ruleengine.datasource.DataSource;
import com.jrules.ruleengine.datasource.DataSourceConfig;
import com.jrules.ruleengine.datasource.config.DataSourceSpecificConfig;
import com.jrules.ruleengine.datasource.config.S3Config;
import com.jrules.ruleengine.datasource.factory.DataSourceFactory;
import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.enums.FileFormat;
import com.jrules.ruleengine.enums.Operator;
import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationError;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import com.jrules.ruleengine.validator.DataSourceValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("s3DataSourceValidator")
@RequiredArgsConstructor
public class S3DataSourceValidator implements DataSourceValidator {

    private final DataSourceFactory dataSourceFactory;

    @Override
    public void validate(RuleSet.Criterion criterion, RuleSetValidationResult validationResult) {
        String name = criterion.getName();
        DataSourceSpecificConfig config = criterion.getDatasource().getConfig();
        
        if (config == null) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Criterion '" + name + "' S3 datasource missing configuration"));
            return;
        }
        
        if (!(config instanceof S3Config s3Config)) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Criterion '" + name + "' S3 datasource has invalid configuration type"));
            return;
        }
        
        if (isNullOrEmpty(s3Config.getBucket())) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Criterion '" + name + "' S3 datasource missing bucket"));
        }
        
        if (isNullOrEmpty(s3Config.getPath())) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Criterion '" + name + "' S3 datasource missing file path"));
        }

        if(isNullOrEmpty(s3Config.getExtractKey())) {
          validationResult.addError(new RuleSetValidationError(
              RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
              "Criterion '" + name + "' S3 datasource missing extraction key"));
        }

        if(s3Config.getFormat() != null &&
            !isNullOrEmpty(s3Config.getPath())) {
          validateFileFormat(criterion,
                             s3Config.getFormat(),
                             s3Config.getPath(),
                             s3Config.getExtractKey(),
                             validationResult);
        }

        if(isNullOrEmpty(s3Config.getPath()) &&
            isNullOrEmpty(s3Config.getBucket()) &&
            isNullOrEmpty(s3Config.getExtractKey()) &&
            s3Config.getFormat() != null) {
          validateS3Values(criterion, validationResult);
        }
    }
    
    private void validateS3Values(RuleSet.Criterion criterion, RuleSetValidationResult validationResult) {
        try {
            DataSourceConfig config = criterion.getDatasource();
            DataSource dataSource = dataSourceFactory.createDataSource(config);
            List<Object> values = dataSource.resolveValues();
            DataSourceValidatorHelper.validateValueTypes(values,
                                                         DataType.from(criterion.getDatasource().getDatatype()),
                                                         Operator.from(criterion.getOperator()),
                                                         criterion.getName(),
                                                         validationResult);
        } catch (Exception e) {
            validationResult.addError(new RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                "Criterion '" + criterion.getName() + "' external datasource validation failed: " + e.getMessage()));
        }
    }
    
    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void validateFileFormat(
        RuleSet.Criterion criterion,
        FileFormat format,
        String key,
        String extractKey,
        RuleSetValidationResult validationResult) {

      String fileExtension = getFileExtension(key);

      switch (format) {
        case JSON:
          if (!"json".equalsIgnoreCase(fileExtension)) {
            validationResult.addError(new RuleSetValidationError(RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                                                                 String.format("Criterion '%s': File format mismatch: expected JSON file but got .%s extension", criterion.getName(), fileExtension)));
          }
          if(!extractKey.startsWith("$")) {
            validationResult.addError(new RuleSetValidationError(RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                                                                 String.format("Criterion '%s': Invalid extractKey. Must start with '$' or '$.': %s", criterion.getName(), extractKey)));
          }
          break;
        case CSV:
          if (!"csv".equalsIgnoreCase(fileExtension)) {
            validationResult.addError(new RuleSetValidationError(RuleSetValidationErrorType.RULESET_CRITERIA_VALIDATION_FAILURE,
                                                                 String.format("Criterion '%s': File format mismatch: expected JSON file but got .%s extension", criterion.getName(), fileExtension)));
          }
          break;
      }
    }

    private String getFileExtension(String fileName) {
      int lastDotIndex = fileName.lastIndexOf('.');
      return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }
}
