package com.jrules.ruleengine.v2.model.lookup;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class FileLookup extends Lookup {

    private String fileRef;
    private String format;
    private String extractKey;

    /** CSV column headers extracted at upload time. Used by the FE for LOOKUP() autocomplete. */
    private List<String> columns;

    @Override
    public LookupType getType() {
        return LookupType.FILE;
    }
}
