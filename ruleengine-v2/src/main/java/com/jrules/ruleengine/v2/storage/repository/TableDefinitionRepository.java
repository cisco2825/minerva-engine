package com.jrules.ruleengine.v2.storage.repository;

import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.TableDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TableDefinitionRepository extends JpaRepository<TableDefinitionEntity, String> {

    Optional<TableDefinitionEntity> findByTableIdAndVersion(String tableId, String version);

    List<TableDefinitionEntity> findByTableIdOrderByCreatedAtDesc(String tableId);

    Optional<TableDefinitionEntity> findFirstByTableIdAndStatusOrderByCreatedAtDesc(String tableId, AssetStatus status);

    boolean existsByTableIdAndVersion(String tableId, String version);
}
