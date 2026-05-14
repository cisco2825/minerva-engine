package com.jrules.ruleengine.v2.storage.repository;

import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LookupDefinitionRepository extends JpaRepository<LookupDefinitionEntity, String> {

    Optional<LookupDefinitionEntity> findByLookupIdAndVersion(String lookupId, String version);

    List<LookupDefinitionEntity> findByLookupIdOrderByCreatedAtDesc(String lookupId);

    Optional<LookupDefinitionEntity> findFirstByLookupIdAndStatusOrderByCreatedAtDesc(String lookupId, AssetStatus status);

    boolean existsByLookupIdAndVersion(String lookupId, String version);
}
