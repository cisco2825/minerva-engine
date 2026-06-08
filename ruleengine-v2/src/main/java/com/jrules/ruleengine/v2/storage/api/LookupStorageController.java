package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.auth.security.AuthUtils;
import com.jrules.ruleengine.v2.s3.S3Service;
import com.jrules.ruleengine.v2.storage.dto.LookupSummary;
import com.jrules.ruleengine.v2.storage.dto.LookupUploadResponse;
import com.jrules.ruleengine.v2.storage.dto.SaveLookupRequest;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import com.jrules.ruleengine.v2.storage.service.LookupStorageService;
import lombok.RequiredArgsConstructor;
import com.jrules.ruleengine.v2.model.lookup.FileLookup;
import com.jrules.ruleengine.v2.model.lookup.InlineLookup;
import com.jrules.ruleengine.v2.model.lookup.Lookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/lookups")
@RequiredArgsConstructor
public class LookupStorageController {

    private final LookupStorageService lookupStorageService;
    private final S3Service s3Service;

    @Value("${ruleengine.s3.lookup-bucket}")
    private String lookupBucket;

    @Value("${ruleengine.lookup.max-file-size-mb:30}")
    private int maxFileSizeMb;

    // ── List (paginated) ──────────────────────────────────────────────────────

    @GetMapping
    public Page<LookupSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lookupStorageService.listPage(page, size);
    }

    // ── Upload CSV to S3 ──────────────────────────────────────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public LookupUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lookupId") String lookupId,
            @RequestParam("version") String version) throws IOException {

        validateFile(file);

        // Read file bytes once — used for both header extraction and S3 upload
        byte[] fileBytes = file.getBytes();

        // Extract CSV column headers from the first line (for LOOKUP() autocomplete in the FE)
        List<String> columns = extractCsvHeaders(fileBytes);

        String safeFileName = sanitize(file.getOriginalFilename());
        String s3Key = "lookups/" + lookupId + "/" + version + "/" + UUID.randomUUID() + "_" + safeFileName;

        s3Service.putObject(
                lookupBucket,
                s3Key,
                new java.io.ByteArrayInputStream(fileBytes),
                fileBytes.length,
                file.getContentType() != null ? file.getContentType() : "text/csv"
        );

        String fileRef = lookupBucket + "/" + s3Key;
        return new LookupUploadResponse(fileRef, safeFileName, fileBytes.length, columns);
    }

    // ── Save lookup definition ────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LookupDefinitionEntity save(@RequestBody SaveLookupRequest request) {
        request.setCreatedBy(AuthUtils.currentUserName());
        return lookupStorageService.save(request);
    }

    // ── Versions ──────────────────────────────────────────────────────────────

    @GetMapping("/{lookupId}/versions")
    public List<LookupDefinitionEntity> listVersions(@PathVariable String lookupId) {
        return lookupStorageService.getVersions(lookupId);
    }

    @GetMapping("/{lookupId}/versions/{version}")
    public LookupDefinitionEntity getByVersion(
            @PathVariable String lookupId,
            @PathVariable String version) {
        return lookupStorageService.getByIdAndVersion(lookupId, version);
    }

    // ── Download CSV ──────────────────────────────────────────────────────────
    // FILE lookups → stream from S3.
    // INLINE lookups → generate a single-column CSV on the fly.

    @GetMapping("/{lookupId}/versions/{version}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String lookupId,
            @PathVariable String version) throws IOException {

        LookupDefinitionEntity entity = lookupStorageService.getByIdAndVersion(lookupId, version);
        Lookup lookup = lookupStorageService.deserialize(entity);

        final byte[] bytes;
        if (lookup instanceof FileLookup file) {
            String[] parts = file.getFileRef().split("/", 2);
            if (parts.length != 2) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Malformed fileRef: " + file.getFileRef());
            }
            InputStream stream = s3Service.getObjectWithBucketName(parts[0], parts[1]);
            bytes = stream.readAllBytes();
        } else if (lookup instanceof InlineLookup inline) {
            bytes = buildInlineCsv(inline);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported lookup type for download: " + lookup.getType());
        }

        String filename = lookupId + "_" + version + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(bytes.length);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /** Generates a single-column CSV ( header: {@code value} ) from an inline lookup list. */
    private byte[] buildInlineCsv(InlineLookup inline) {
        StringBuilder sb = new StringBuilder("value\n");
        if (inline.getValues() != null) {
            for (Object v : inline.getValues()) {
                String cell = v == null ? "" : v.toString();
                // Wrap in quotes if the value contains a comma, newline, or double-quote
                if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
                    cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                }
                sb.append(cell).append("\n");
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @PatchMapping("/{lookupId}/versions/{version}/status")
    public LookupDefinitionEntity updateStatus(
            @PathVariable String lookupId,
            @PathVariable String version,
            @RequestParam AssetStatus status) {
        return lookupStorageService.updateStatus(lookupId, version, status);
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @DeleteMapping("/{lookupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String lookupId) {
        lookupStorageService.delete(lookupId);
    }

    // ── Column metadata (on-demand) ───────────────────────────────────────────
    // Returns column headers for the latest active version of a lookup.
    // If not yet stored on the body, reads just the first line from S3, persists it,
    // and returns it — so this call also auto-backfills that one lookup.

    @GetMapping("/{lookupId}/columns")
    public List<String> getColumns(@PathVariable String lookupId) {
        var entityOpt = lookupStorageService.getLatestActive(lookupId);
        if (entityOpt.isEmpty()) entityOpt = lookupStorageService.getLatestAny(lookupId);
        if (entityOpt.isEmpty()) return List.of();

        var entity = entityOpt.get();
        com.jrules.ruleengine.v2.model.lookup.Lookup raw = lookupStorageService.deserialize(entity);
        if (!(raw instanceof com.jrules.ruleengine.v2.model.lookup.FileLookup fl)) return List.of();

        // Return stored columns if already populated
        if (fl.getColumns() != null && !fl.getColumns().isEmpty()) return fl.getColumns();

        // Otherwise read just the header row from S3, persist, and return
        try {
            String[] parts = fl.getFileRef().split("/", 2);
            if (parts.length != 2) return List.of();
            try (java.io.InputStream stream = s3Service.getObjectWithBucketName(parts[0], parts[1])) {
                List<String> cols = extractCsvHeaders(stream.readNBytes(4096));
                if (!cols.isEmpty()) {
                    fl.setColumns(cols);
                    lookupStorageService.updateBody(entity, fl);
                }
                return cols;
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Backfill column metadata ──────────────────────────────────────────────
    // One-time admin call: reads the first line of every FILE lookup that has no
    // column metadata yet and persists the headers. Safe to call multiple times.

    @PostMapping("/admin/backfill-columns")
    public java.util.Map<String, Object> backfillColumns() throws Exception {
        int updated = 0;
        int skipped = 0;
        int failed  = 0;

        var all = lookupStorageService.findAllFileLookups();
        for (var entity : all) {
            try {
                com.jrules.ruleengine.v2.model.lookup.Lookup raw = lookupStorageService.deserialize(entity);
                if (!(raw instanceof com.jrules.ruleengine.v2.model.lookup.FileLookup fl)) { skipped++; continue; }
                if (fl.getColumns() != null && !fl.getColumns().isEmpty()) { skipped++; continue; }

                // Fetch just enough bytes to read the first line
                String[] parts = fl.getFileRef().split("/", 2);
                if (parts.length != 2) { failed++; continue; }
                try (java.io.InputStream stream = s3Service.getObjectWithBucketName(parts[0], parts[1])) {
                    byte[] buf = stream.readNBytes(4096); // header row fits in 4 KB
                    List<String> cols = extractCsvHeaders(buf);
                    if (cols.isEmpty()) { skipped++; continue; }
                    fl.setColumns(cols);
                    lookupStorageService.updateBody(entity, fl);
                    updated++;
                }
            } catch (Exception e) {
                failed++;
            }
        }
        return java.util.Map.of("updated", updated, "skipped", skipped, "failed", failed);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File exceeds maximum allowed size of " + maxFileSizeMb + " MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CSV files are accepted");
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "upload.csv";
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    /**
     * Reads the first line of a CSV byte array and returns trimmed, non-blank column headers.
     * Returns an empty list if the file is empty or the header line is blank.
     */
    private List<String> extractCsvHeaders(byte[] fileBytes) {
        try {
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            String firstLine = content.lines().findFirst().orElse("").trim();
            if (firstLine.isBlank()) return List.of();
            return Arrays.stream(firstLine.split(","))
                    .map(String::trim)
                    .filter(h -> !h.isBlank())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
