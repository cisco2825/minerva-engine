package com.jrules.ruleengine.v2.storage.entity;

import com.jrules.ruleengine.v2.model.enums.TraceLevel;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Entity
@Table(name = "evaluation_log")
public class EvaluationLogEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "policy_id", nullable = false, length = 100)
    private String policyId;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Column(name = "policy_def_id", nullable = false, length = 36)
    private String policyDefId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EvaluationStatus status;

    @Column(length = 100)
    private String outcome;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "LONGTEXT")
    private String context;

    @Column(columnDefinition = "LONGTEXT")
    private String result;

    @Enumerated(EnumType.STRING)
    @Column(name = "trace_level", length = 10)
    private TraceLevel traceLevel;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "evaluated_by", length = 100)
    private String evaluatedBy;

    @CreationTimestamp
    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;
}
