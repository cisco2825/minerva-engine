package com.jrules.ruleengine.v2.storage.entity;

import com.jrules.ruleengine.v2.model.lookup.LookupType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "lookup_definition")
@SQLRestriction("deleted = false")
@Getter
@Setter
public class LookupDefinitionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "lookup_id", nullable = false, length = 100)
    private String lookupId;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LookupType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.DRAFT;

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

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean deleted = false;
}
