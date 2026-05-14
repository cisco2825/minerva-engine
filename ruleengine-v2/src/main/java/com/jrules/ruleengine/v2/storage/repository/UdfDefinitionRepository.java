package com.jrules.ruleengine.v2.storage.repository;

import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.UdfDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UdfDefinitionRepository extends JpaRepository<UdfDefinitionEntity, String> {

    Optional<UdfDefinitionEntity> findByUdfIdAndVersion(String udfId, String version);

    List<UdfDefinitionEntity> findByUdfIdOrderByCreatedAtDesc(String udfId);

    Optional<UdfDefinitionEntity> findFirstByUdfIdAndStatusOrderByCreatedAtDesc(String udfId, AssetStatus status);

    boolean existsByUdfIdAndVersion(String udfId, String version);
}
