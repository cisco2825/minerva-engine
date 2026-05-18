package com.jrules.ruleengine.v2.storage.dto;

import lombok.Data;

@Data
public class PolicyStatsDto {
    /** Total number of unique policies (regardless of version count). */
    private long total;
    /** Policies that have at least one ACTIVE version. */
    private long live;
    /** Policies that exist but have no ACTIVE version (drafts, inactive, archived). */
    private long unpublished;
    /** Total evaluation runs in the last 7 days across all policies. */
    private long evaluations7d;
}
