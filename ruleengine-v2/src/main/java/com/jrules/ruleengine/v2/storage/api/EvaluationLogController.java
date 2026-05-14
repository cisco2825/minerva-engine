package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.storage.dto.EvaluationLogSummary;
import com.jrules.ruleengine.v2.storage.entity.EvaluationLogEntity;
import com.jrules.ruleengine.v2.storage.service.PolicyStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EvaluationLogController {

    private final PolicyStorageService policyStorageService;

    @GetMapping("/api/v2/policies/{policyId}/evaluations")
    public Page<EvaluationLogSummary> listByPolicy(
            @PathVariable String policyId,
            @PageableDefault(size = 20, sort = "evaluatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return policyStorageService.listEvaluations(policyId, pageable)
                .map(EvaluationLogSummary::from);
    }

    @GetMapping("/api/v2/policies/{policyId}/versions/{version}/evaluations")
    public Page<EvaluationLogSummary> listByVersion(
            @PathVariable String policyId,
            @PathVariable String version,
            @PageableDefault(size = 20, sort = "evaluatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return policyStorageService.listEvaluationsByVersion(policyId, version, pageable)
                .map(EvaluationLogSummary::from);
    }

    @GetMapping("/api/v2/evaluations/{id}")
    public EvaluationLogEntity getById(@PathVariable String id) {
        return policyStorageService.getEvaluationLog(id);
    }
}
