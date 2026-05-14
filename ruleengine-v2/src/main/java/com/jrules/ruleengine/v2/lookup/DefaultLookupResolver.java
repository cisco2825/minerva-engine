package com.jrules.ruleengine.v2.lookup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.model.lookup.FileLookup;
import com.jrules.ruleengine.v2.model.lookup.InlineLookup;
import com.jrules.ruleengine.v2.model.lookup.Lookup;
import com.jrules.ruleengine.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultLookupResolver implements LookupResolver {

    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    @Override
    public List<Object> resolve(String name, Map<String, Lookup> lookups) {
        if (lookups == null || !lookups.containsKey(name)) {
            throw new EvaluationException("Lookup '@" + name + "' not found in request");
        }
        Lookup lookup = lookups.get(name);
        if (lookup instanceof InlineLookup inline) {
            return inline.getValues();
        }
        if (lookup instanceof FileLookup file) {
            return resolveFile(name, file);
        }
        throw new EvaluationException("Unknown lookup type for '@" + name + "'");
    }

    @Cacheable(value = "lookups", key = "#file.fileRef")
    public List<Object> resolveFile(String name, FileLookup file) {
        log.debug("Resolving FILE lookup '{}' from S3: {}", name, file.getFileRef());
        try {
            String[] parts = file.getFileRef().split("/", 2);
            if (parts.length != 2) {
                throw new EvaluationException("FILE lookup fileRef must be 'bucket/key', got: " + file.getFileRef());
            }
            String bucket = parts[0];
            String key = parts[1];
            InputStream stream = s3Service.getObjectWithBucketName(bucket, key);

            return switch (file.getFormat().toUpperCase()) {
                case "JSON" -> readJson(stream, file.getExtractKey());
                case "CSV"  -> readCsv(stream, file.getExtractKey());
                default -> throw new EvaluationException("Unsupported FILE lookup format: " + file.getFormat());
            };
        } catch (EvaluationException e) {
            throw e;
        } catch (Exception e) {
            throw new EvaluationException("Failed to resolve FILE lookup '@" + name + "': " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> readJson(InputStream stream, String extractKey) throws Exception {
        if (extractKey == null || extractKey.isBlank()) {
            return objectMapper.readValue(stream, new TypeReference<List<Object>>() {});
        }
        Map<String, Object> root = objectMapper.readValue(stream, new TypeReference<>() {});
        Object value = root.get(extractKey);
        if (!(value instanceof List<?> list)) {
            throw new EvaluationException("JSON FILE lookup: extractKey '" + extractKey + "' did not resolve to a list");
        }
        return (List<Object>) list;
    }

    private List<Object> readCsv(InputStream stream, String extractKey) throws Exception {
        // Read the column identified by extractKey (column name or 0-based index)
        String content = new String(stream.readAllBytes());
        String[] lines = content.split("\n");
        if (lines.length == 0) return List.of();

        String[] headers = lines[0].trim().split(",");
        int colIdx = resolveColumnIndex(headers, extractKey);

        java.util.List<Object> values = new java.util.ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            String[] cols = line.split(",");
            if (colIdx < cols.length) {
                values.add(cols[colIdx].trim());
            }
        }
        return values;
    }

    private int resolveColumnIndex(String[] headers, String extractKey) {
        if (extractKey == null || extractKey.isBlank()) return 0;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(extractKey)) return i;
        }
        try {
            return Integer.parseInt(extractKey);
        } catch (NumberFormatException e) {
            throw new EvaluationException("CSV FILE lookup: column '" + extractKey + "' not found in headers");
        }
    }
}
