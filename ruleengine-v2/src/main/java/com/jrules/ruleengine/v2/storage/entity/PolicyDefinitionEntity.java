package com.jrules.ruleengine.v2.storage.entity;

import com.jrules.ruleengine.v2.model.enums.PolicyType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "policy_definition")
@Getter
@Setter
public class PolicyDefinitionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "policy_id", nullable = false, length = 100)
    private String policyId;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status = PolicyStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "policy_table_ref",
            joinColumns = @JoinColumn(name = "policy_def_id"),
            inverseJoinColumns = @JoinColumn(name = "table_def_id")
    )
    private Set<TableDefinitionEntity> tables = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "policy_lookup_ref",
            joinColumns = @JoinColumn(name = "policy_def_id"),
            inverseJoinColumns = @JoinColumn(name = "lookup_def_id")
    )
    private Set<LookupDefinitionEntity> lookups = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "policy_udf_ref",
            joinColumns = @JoinColumn(name = "policy_def_id"),
            inverseJoinColumns = @JoinColumn(name = "udf_def_id")
    )
    private Set<UdfDefinitionEntity> udfs = new HashSet<>();
}
