package com.jrules.ruleengine.v2.model.lookup;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class FileLookup extends Lookup {

    private String fileRef;
    private String format;
    private String extractKey;

    @Override
    public LookupType getType() {
        return LookupType.FILE;
    }
}
