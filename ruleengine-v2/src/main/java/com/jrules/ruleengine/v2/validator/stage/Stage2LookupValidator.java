package com.jrules.ruleengine.v2.validator.stage;

import com.jrules.ruleengine.v2.model.lookup.FileLookup;
import com.jrules.ruleengine.v2.model.lookup.InlineLookup;
import com.jrules.ruleengine.v2.model.lookup.Lookup;
import com.jrules.ruleengine.v2.validator.ValidationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class Stage2LookupValidator {

    private static final String STAGE = "STAGE_2_LOOKUP";
    private static final Set<String> VALID_FORMATS = Set.of("JSON", "CSV");

    public void validate(ValidationContext ctx) {
        Map<String, Lookup> lookups = ctx.getRequest().getLookups();
        if (lookups == null || lookups.isEmpty()) return;

        for (Map.Entry<String, Lookup> entry : lookups.entrySet()) {
            String name = entry.getKey();
            Lookup lookup = entry.getValue();
            String loc = "lookups[" + name + "]";

            if (isBlank(name)) {
                ctx.error("LOOKUP.BLANK_KEY", STAGE, loc, "Lookup key (name) must not be blank");
                continue;
            }
            if (lookup == null) {
                ctx.error("LOOKUP.NULL_VALUE", STAGE, loc, "Lookup '" + name + "' has a null definition");
                continue;
            }

            if (lookup instanceof InlineLookup inline) {
                if (inline.getValues() == null || inline.getValues().isEmpty()) {
                    ctx.error("LOOKUP.INLINE_EMPTY", STAGE, loc,
                            "INLINE lookup '" + name + "' must have at least one value",
                            "Add values to the 'values' array");
                }
            } else if (lookup instanceof FileLookup file) {
                if (isBlank(file.getFileRef())) {
                    ctx.error("LOOKUP.FILE_REF_BLANK", STAGE, loc,
                            "FILE lookup '" + name + "' must have a non-blank fileRef",
                            "Set fileRef to 'bucket/key'");
                } else if (!file.getFileRef().contains("/")) {
                    ctx.error("LOOKUP.FILE_REF_INVALID", STAGE, loc,
                            "FILE lookup '" + name + "' fileRef must be in 'bucket/key' format",
                            "Example: 'my-bucket/lookups/cities.json'");
                }
                if (isBlank(file.getFormat())) {
                    ctx.error("LOOKUP.FILE_FORMAT_BLANK", STAGE, loc,
                            "FILE lookup '" + name + "' must specify a format",
                            "Set format to one of: " + VALID_FORMATS);
                } else if (!VALID_FORMATS.contains(file.getFormat().toUpperCase())) {
                    ctx.error("LOOKUP.FILE_FORMAT_INVALID", STAGE, loc,
                            "FILE lookup '" + name + "' has unsupported format: '" + file.getFormat() + "'",
                            "Supported formats: " + VALID_FORMATS);
                }
                if (file.getFormat() != null && "JSON".equalsIgnoreCase(file.getFormat())
                        && isBlank(file.getExtractKey())) {
                    ctx.warning("LOOKUP.FILE_EXTRACT_KEY_MISSING", STAGE, loc,
                            "FILE lookup '" + name + "' (JSON) has no extractKey — the entire file will be treated as a list");
                }
            }
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
