package com.jrules.ruleengine.v2.model.graph.config;

import lombok.Data;

import java.util.List;

@Data
public class SourceNodeConfig {

    /**
     * Sources as saved by the studio UI — each entry is a SourceItem object
     * with { type, id, label }. The engine uses only the id at runtime.
     */
    private List<SourceItem> sources;

    @Data
    public static class SourceItem {
        /** Source category: "lookup", "finboxSource", "customSource", … */
        private String type;
        /** The identifier used at runtime (e.g. lookup ID). */
        private String id;
        /** Human-readable label shown in the studio — ignored by the engine. */
        private String label;
    }
}
