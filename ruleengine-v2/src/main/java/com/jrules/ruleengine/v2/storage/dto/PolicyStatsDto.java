package com.jrules.ruleengine.v2.storage.dto;

import lombok.Data;

@Data
public class PolicyStatsDto {
    private long total;
    private long active;
    private long draft;
    private long inactive;
    private long archived;
}
