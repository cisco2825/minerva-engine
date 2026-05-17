package com.jrules.ruleengine.v2.storage.repository;

import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LookupDefinitionRepository extends JpaRepository<LookupDefinitionEntity, String> {

    Optional<LookupDefinitionEntity> findByLookupIdAndVersion(String lookupId, String version);

    List<LookupDefinitionEntity> findByLookupIdOrderByCreatedAtDesc(String lookupId);

    Optional<LookupDefinitionEntity> findFirstByLookupIdAndStatusOrderByCreatedAtDesc(String lookupId, AssetStatus status);

    boolean existsByLookupIdAndVersion(String lookupId, String version);

    /**
     * Paginated list of the latest version per lookup (for the list page).
     * @SQLRestriction on the entity handles the deleted = false filter automatically.
     */
    @Query("""
            SELECT e FROM LookupDefinitionEntity e
            WHERE e.createdAt = (
                SELECT MAX(e2.createdAt) FROM LookupDefinitionEntity e2
                WHERE e2.lookupId = e.lookupId
            )
            ORDER BY e.updatedAt DESC
            """)
    Page<LookupDefinitionEntity> findLatestVersionPerLookup(Pageable pageable);
}
