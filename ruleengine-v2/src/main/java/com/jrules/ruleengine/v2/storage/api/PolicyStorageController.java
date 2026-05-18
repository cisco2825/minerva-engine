package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.storage.dto.EvaluateStoredRequest;
import com.jrules.ruleengine.v2.storage.dto.PolicySummary;
import com.jrules.ruleengine.v2.storage.dto.SavePolicyRequest;
import com.jrules.ruleengine.v2.storage.dto.UpdateStatusRequest;
import com.jrules.ruleengine.v2.auth.security.AuthUtils;
import com.jrules.ruleengine.v2.storage.service.PolicyStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/policies")
@RequiredArgsConstructor
public class PolicyStorageController {

    private final PolicyStorageService policyStorageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicySummary save(@RequestBody SavePolicyRequest request) {
        request.setCreatedBy(AuthUtils.currentUserName());
        return PolicySummary.from(policyStorageService.save(request));
    }

    @GetMapping
    public Page<PolicySummary> listAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return policyStorageService.listPaginated(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{policyId}")
    public PolicySummary getLatestActive(@PathVariable String policyId) {
        return PolicySummary.from(policyStorageService.getLatestActive(policyId));
    }

    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolicy(@PathVariable String policyId) {
        policyStorageService.deletePolicy(policyId);
    }

    @GetMapping("/{policyId}/versions")
    public List<PolicySummary> listVersions(@PathVariable String policyId) {
        return policyStorageService.listVersions(policyId);
    }

    @GetMapping("/{policyId}/versions/{version}")
    public PolicySummary getByVersion(@PathVariable String policyId, @PathVariable String version) {
        return PolicySummary.from(policyStorageService.getByVersion(policyId, version));
    }

    @GetMapping("/{policyId}/versions/{version}/definition")
    public Policy getDefinition(@PathVariable String policyId, @PathVariable String version) {
        return policyStorageService.getDefinition(policyId, version);
    }

    @PutMapping("/{policyId}/versions/{version}")
    public PolicySummary updateDraft(
            @PathVariable String policyId,
            @PathVariable String version,
            @RequestBody SavePolicyRequest request) {
        request.setCreatedBy(AuthUtils.currentUserName());
        return PolicySummary.from(policyStorageService.updateDraft(policyId, version, request));
    }

    @PatchMapping("/{policyId}/versions/{version}/status")
    public PolicySummary updateStatus(
            @PathVariable String policyId,
            @PathVariable String version,
            @RequestBody UpdateStatusRequest request) {
        return PolicySummary.from(policyStorageService.updateStatus(policyId, version, request));
    }

    @PostMapping("/{policyId}/evaluate")
    public EvaluationResult evaluateLatestActive(
            @PathVariable String policyId,
            @RequestBody EvaluateStoredRequest request) {
        return policyStorageService.evaluateLatestActive(policyId, request);
    }

    @PostMapping("/{policyId}/versions/{version}/evaluate")
    public EvaluationResult evaluateByVersion(
            @PathVariable String policyId,
            @PathVariable String version,
            @RequestBody EvaluateStoredRequest request) {
        return policyStorageService.evaluateByVersion(policyId, version, request);
    }
}
