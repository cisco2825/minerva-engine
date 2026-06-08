package com.jrules.ruleengine.v2.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LookupUploadResponse {
    /** S3 reference in the form "bucket/key", to be passed back in SaveLookupRequest */
    private String fileRef;
    private String originalFileName;
    private long fileSizeBytes;
    /** CSV column headers from the first row — used by the FE for LOOKUP() autocomplete. */
    private List<String> columns;
}
