package com.jrules.ruleengine.v2.storage.repository;

import com.jrules.ruleengine.v2.storage.entity.PolicyDefinitionEntity;
import com.jrules.ruleengine.v2.storage.entity.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyDefinitionRepository extends JpaRepository<PolicyDefinitionEntity, String> {

    Optional<PolicyDefinitionEntity> findByPolicyIdAndVersion(String policyId, String version);

    List<PolicyDefinitionEntity> findByPolicyIdOrderByCreatedAtDesc(String policyId);

    List<PolicyDefinitionEntity> findAllByOrderByCreatedAtDesc();

    List<PolicyDefinitionEntity> findByStatusOrderByCreatedAtDesc(PolicyStatus status);

    // Latest active version = most recently created ACTIVE entry for this policyId
    @Query("SELECT p FROM PolicyDefinitionEntity p WHERE p.policyId = :policyId AND p.status = 'ACTIVE' ORDER BY p.createdAt DESC")
    List<PolicyDefinitionEntity> findActiveByPolicyId(@Param("policyId") String policyId);

    boolean existsByPolicyIdAndVersion(String policyId, String version);

    /**
     * One row per unique policyId — the most recently created version of each policy.
     * Used for the paginated policy list.
     */
    @Query(value = "SELECT p FROM PolicyDefinitionEntity p " +
                   "WHERE p.createdAt = (SELECT MAX(p2.createdAt) FROM PolicyDefinitionEntity p2 WHERE p2.policyId = p.policyId) " +
                   "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p.policyId) FROM PolicyDefinitionEntity p")
    Page<PolicyDefinitionEntity> findLatestPerPolicy(Pageable pageable);

    /**
     * Version counts for a given set of policyIds. Returns [policyId, count] pairs.
     */
    @Query("SELECT p.policyId, COUNT(p) FROM PolicyDefinitionEntity p WHERE p.policyId IN :policyIds GROUP BY p.policyId")
    List<Object[]> countVersionsByPolicyIds(@Param("policyIds") List<String> policyIds);

    /**
     * Count of unique policies grouped by the status of their latest version.
     * Returns [PolicyStatus, count] pairs.
     */
    @Query("SELECT p.status, COUNT(p) FROM PolicyDefinitionEntity p " +
           "WHERE p.createdAt = (SELECT MAX(p2.createdAt) FROM PolicyDefinitionEntity p2 WHERE p2.policyId = p.policyId) " +
           "GROUP BY p.status")
    List<Object[]> countByLatestStatus();
}
