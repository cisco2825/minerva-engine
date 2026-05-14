package com.jrules.ruleengine.datasource.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.cache.CacheServiceable;
import com.jrules.ruleengine.datasource.DataSource;
import com.jrules.ruleengine.s3.S3Service;
import com.jrules.ruleengine.enums.DataType;
import com.jrules.ruleengine.enums.FileFormat;
import com.jrules.ruleengine.utils.ParsingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DataSource implementation that fetches values from S3 buckets.
 * 
 * <p>Supports CSV and JSON file formats with caching for performance.
 * Extracted values are validated against the expected data type.
 * 
 * <p>JSON extraction supports:
 * <ul>
 *   <li>"$" or null - returns entire document</li>
 *   <li>"$.path.to.field" - navigates to nested field</li>
 * </ul>
 * 
 * <p>CSV extraction uses column name from extractKey to locate values.
 */
@Slf4j
@RequiredArgsConstructor
public class S3DataSource implements DataSource {

    private static final String CACHE_MAP = "RULE_ENGINE_S3_DATASOURCE";
    private static final Long CACHE_TTL_SECONDS = 300L;

    private final String bucket;
    private final String key;
    private final FileFormat format;
    private final String extractKey;
    private final DataType expectedDataType;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final CacheServiceable cacheServiceable;

    /**
     * Resolves values from S3 with caching support.
     * 
     * @return list of extracted values from S3 file
     * @throws Problem if S3 fetch fails, file format is invalid, or values don't match expected data type
     *
     * @author Shubham Thakur
     */
    @Override
    public List<Object> resolveValues() {
        log.debug("Fetching values from S3: bucket={}, key={}", bucket, key);

      String cacheKey = buildCacheKey();

      // 1. Try cache first
      List<Object> cachedValues =
          cacheServiceable.getFromCache(CACHE_MAP, cacheKey);

      if (cachedValues != null) {
        log.debug("Cache hit for S3 datasource: {}", cacheKey);
        return cachedValues;
      }

      log.debug("Cache miss. Fetching values from S3: bucket={}, key={}", bucket, key);

      try(InputStream inputStream = s3Service.getObjectWithBucketName(bucket, key)){
            List<Object> values = switch (format) {
              case JSON -> extractFromJson(inputStream);
              case CSV -> extractFromCsv(inputStream);
              default -> throw Problem.valueOf(Status.BAD_REQUEST, "Unsupported format: " + format);
            };

            validateExtractedValues(values);

            cacheServiceable.storeInCache(
                CACHE_MAP,
                cacheKey,
                values,
                CACHE_TTL_SECONDS,
                TimeUnit.SECONDS
            );

            log.debug("Cached {} values for key={}", values.size(), cacheKey);

            return values;

        } catch (Exception e) {
            log.error("Failed to fetch values from S3: bucket={}, key={}", bucket, key, e);
            throw Problem.valueOf(Status.INTERNAL_SERVER_ERROR, "Failed to fetch values from S3: " + e.getMessage());
        }
    }
    
    /**
     * Validates extracted values are non-empty and match expected data type.
     * 
     * @param values the extracted values to validate
     * @throws Problem if values are empty or don't match expected data type
     */
    private void validateExtractedValues(List<Object> values) {
        if (values == null || values.isEmpty()) {
            throw Problem.valueOf(Status.BAD_REQUEST, "No values extracted from S3 file");
        }
        
        for (Object value : values) {
            if (!ParsingUtils.isParsableAs(value, expectedDataType)) {
                throw Problem.valueOf(Status.BAD_REQUEST, 
                    String.format("Value '%s' from the datasource is not parseable as %s",
                                  value, expectedDataType));
            }
        }
        
        log.debug("Successfully validated {} values from S3 file", values.size());
    }

    /**
     * Extracts values from JSON file using JSONPath-like syntax.
     * 
     * @param inputStream the JSON file input stream
     * @return list of extracted values
     * @throws Exception if JSON parsing fails or extractKey path is invalid
     */
    private List<Object> extractFromJson(InputStream inputStream) throws Exception {
    JsonNode rootNode = objectMapper.readTree(inputStream);

    // Case 1: extractKey is null or "$" → return entire document
    if (extractKey == null || "$".equals(extractKey)) {
      return convertNodeToList(rootNode);
    }

    // Case 2: must start with "$."
    if (!extractKey.startsWith("$.")) {
      throw Problem.valueOf(
          Status.BAD_REQUEST,
          "Invalid extractKey. Must start with '$' or '$.': " + extractKey
      );
    }

    String normalizedPath = extractKey.substring(2); // remove "$."
    JsonNode targetNode = navigateToNestedKey(rootNode, normalizedPath);

    if (targetNode == null) {
      throw Problem.valueOf(
          Status.BAD_REQUEST,
          "Key path not found in JSON: " + extractKey
      );
    }

    return convertNodeToList(targetNode);
  }

    /**
     * Converts JsonNode to List, handling both array and single value nodes.
     * 
     * @param node the JSON node to convert
     * @return list of objects
     */
    private List<Object> convertNodeToList(JsonNode node) {
      if (node.isArray()) {
        return objectMapper.convertValue(
            node,
            new TypeReference<List<Object>>() {}
        );
      }

      return List.of(objectMapper.convertValue(node, Object.class));
    }

    /**
     * Navigates to nested JSON field using dot notation.
     * 
     * @param node the root JSON node
     * @param keyPath the dot-separated path (e.g., "data.items")
     * @return the target node or null if path not found
     */
    private JsonNode navigateToNestedKey(JsonNode node, String keyPath) {
        for (String key : keyPath.split("\\.")) {
            if (node == null || !node.has(key)) return null;
            node = node.get(key);
        }
        return node;
    }
    
    /**
     * Extracts values from CSV file using column name.
     * 
     * @param inputStream the CSV file input stream
     * @return list of values from the specified column
     * @throws Exception if CSV parsing fails or column not found
     */
    private List<Object> extractFromCsv(InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw Problem.valueOf(Status.BAD_REQUEST, "Empty CSV file");
            }
            
            String[] headers = headerLine.split(",");
            int columnIndex = findColumnIndex(headers, extractKey);
            
            return reader.lines()
                    .map(line -> line.split(","))
                    .filter(columns -> columnIndex < columns.length)
                    .map(columns -> (Object) columns[columnIndex].trim())
                    .toList();
        }
    }
    
    /**
     * Finds column index by name in CSV headers.
     * 
     * @param headers the CSV header row
     * @param extractKey the column name to find
     * @return column index (0-based)
     * @throws Problem if column not found
     */
    private int findColumnIndex(String[] headers, String extractKey) {
        if (extractKey == null) return 0;
        
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equals(extractKey)) {
                return i;
            }
        }
        throw Problem.valueOf(Status.BAD_REQUEST, "Column not found in CSV: " + extractKey);
    }

    /**
     * Builds unique cache key from S3 datasource configuration.
     * 
     * @return cache key string
     */
    private String buildCacheKey() {
      return String.join("|",
                         bucket,
                         key,
                         format.name(),
                         extractKey != null ? extractKey : "NO_EXTRACT_KEY",
                         expectedDataType.name()
      );
    }

}
