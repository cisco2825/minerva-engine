package com.jrules.ruleengine.v2.storage.repository;

import com.jrules.ruleengine.v2.storage.entity.EvaluationLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface EvaluationLogRepository extends JpaRepository<EvaluationLogEntity, String> {

    Page<EvaluationLogEntity> findByPolicyId(String policyId, Pageable pageable);

    Page<EvaluationLogEntity> findByPolicyIdAndPolicyVersion(String policyId, String policyVersion, Pageable pageable);

    @Query("SELECT COUNT(e) FROM EvaluationLogEntity e WHERE e.evaluatedAt >= :since")
    long countSince(@Param("since") Instant since);
}
