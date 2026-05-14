package com.jrules.ruleengine.v2.storage.entity;

import com.jrules.ruleengine.v2.model.enums.DataType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "udf_definition")
@Getter
@Setter
public class UdfDefinitionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "udf_id", nullable = false, length = 100)
    private String udfId;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false, length = 10)
    private DataType returnType;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String params;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
