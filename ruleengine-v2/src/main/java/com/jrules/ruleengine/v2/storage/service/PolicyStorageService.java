package com.jrules.ruleengine.v2.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.v2.evaluator.rule.RuleChainEvaluator;
import com.jrules.ruleengine.v2.evaluator.scorecard.ScorecardEvaluator;
import com.jrules.ruleengine.v2.evaluator.table.DecisionTableEvaluator;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.NotFoundException;
import com.jrules.ruleengine.v2.model.enums.PolicyType;
import com.jrules.ruleengine.v2.model.lookup.Lookup;
import com.jrules.ruleengine.v2.model.policy.Policy;
import com.jrules.ruleengine.v2.model.request.EvaluationRequest;
import com.jrules.ruleengine.v2.model.request.ValidationRequest;
import com.jrules.ruleengine.v2.model.result.EvaluationResult;
import com.jrules.ruleengine.v2.model.result.ValidationResult;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.model.udf.UDF;
import com.jrules.ruleengine.v2.storage.dto.EvaluateStoredRequest;
import com.jrules.ruleengine.v2.storage.dto.PolicyStatsDto;
import com.jrules.ruleengine.v2.storage.dto.PolicySummary;
import com.jrules.ruleengine.v2.storage.dto.SavePolicyRequest;
import com.jrules.ruleengine.v2.storage.dto.UpdateStatusRequest;
import com.jrules.ruleengine.v2.storage.entity.PolicyDefinitionEntity;
import com.jrules.ruleengine.v2.storage.entity.PolicyStatus;
import com.jrules.ruleengine.v2.storage.entity.TableDefinitionEntity;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import com.jrules.ruleengine.v2.storage.entity.UdfDefinitionEntity;
import com.jrules.ruleengine.v2.storage.entity.EvaluationLogEntity;
import com.jrules.ruleengine.v2.storage.entity.EvaluationStatus;
import com.jrules.ruleengine.v2.storage.repository.EvaluationLogRepository;
import com.jrules.ruleengine.v2.storage.repository.PolicyDefinitionRepository;
import com.jrules.ruleengine.v2.validator.RequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PolicyStorageService {

    private final PolicyDefinitionRepository policyRepo;
    private final EvaluationLogRepository evaluationLogRepo;
    private final TableStorageService tableStorageService;
    private final LookupStorageService lookupStorageService;
    private final UdfStorageService udfStorageService;
    private final RequestValidator requestValidator;
    private final RuleChainEvaluator ruleChainEvaluator;
    private final DecisionTableEvaluator decisionTableEvaluator;
    private final ScorecardEvaluator scorecardEvaluator;
    private final ObjectMapper objectMapper;

    @Transactional
    public PolicyDefinitionEntity save(SavePolicyRequest req) {
        Policy policy = req.getPolicy();
        if (policy == null) throw new IllegalArgumentException("policy is required");
        if (policy.getId() == null || policy.getId().isBlank())
            throw new IllegalArgumentException("policy.id is required");
        if (policy.getVersion() == null || policy.getVersion().isBlank())
            throw new IllegalArgumentException("policy.version is required");

        if (policyRepo.existsByPolicyIdAndVersion(policy.getId(), policy.getVersion())) {
            throw new IllegalArgumentException(
                    "Policy '" + policy.getId() + "' version '" + policy.getVersion() + "' already exists");
        }

        // Validate before storing
        ValidationRequest valReq = new ValidationRequest();
        valReq.setPolicy(policy);
        valReq.setUdfs(req.getUdfs());
        valReq.setTables(req.getTables());
        valReq.setLookups(req.getLookups());
        ValidationResult result = requestValidator.validate(valReq);
        if (!result.isValid()) {
            throw new IllegalArgumentException(formatValidationErrors(result));
        }

        String version = policy.getVersion();
        String createdBy = req.getCreatedBy();

        // Persist standalone tables and wire refs
        List<TableDefinitionEntity> tableEntities = new ArrayList<>();
        if (req.getTables() != null) {
            for (Map.Entry<String, DecisionTable> entry : req.getTables().entrySet()) {
                tableEntities.add(tableStorageService.findOrCreate(entry.getKey(), version, entry.getValue(), createdBy));
            }
        }

        // Persist lookups and wire refs
        List<LookupDefinitionEntity> lookupEntities = new ArrayList<>();
        if (req.getLookups() != null) {
            for (Map.Entry<String, Lookup> entry : req.getLookups().entrySet()) {
                lookupEntities.add(lookupStorageService.findOrCreate(entry.getKey(), version, entry.getValue(), createdBy));
            }
        }

        // Persist UDFs and wire refs
        List<UdfDefinitionEntity> udfEntities = new ArrayList<>();
        if (req.getUdfs() != null) {
            for (UDF udf : req.getUdfs()) {
                udfEntities.add(udfStorageService.findOrCreate(version, udf, createdBy));
            }
        }

        PolicyDefinitionEntity entity = new PolicyDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setPolicyId(policy.getId());
        entity.setVersion(version);
        entity.setName(policy.getName() != null ? policy.getName() : policy.getId());
        entity.setType(policy.getType());
        entity.setStatus(PolicyStatus.DRAFT);
        entity.setDescription(req.getDescription());
        entity.setBody(toJson(policy));
        entity.setCreatedBy(createdBy);
        entity.getTables().addAll(tableEntities);
        entity.getLookups().addAll(lookupEntities);
        entity.getUdfs().addAll(udfEntities);

        return policyRepo.save(entity);
    }

    @Transactional
    public PolicyDefinitionEntity updateStatus(String policyId, String version, UpdateStatusRequest req) {
        PolicyDefinitionEntity entity = policyRepo.findByPolicyIdAndVersion(policyId, version)
                .orElseThrow(() -> new NotFoundException("Policy '" + policyId + "' version '" + version + "' not found"));

        validateStatusTransition(entity.getStatus(), req.getStatus());
        entity.setStatus(req.getStatus());
        return policyRepo.save(entity);
    }

    public PolicyDefinitionEntity getLatestActive(String policyId) {
        List<PolicyDefinitionEntity> active = policyRepo.findActiveByPolicyId(policyId);
        if (active.isEmpty()) {
            throw new NotFoundException("No active version found for policy '" + policyId + "'");
        }
        return active.get(0);
    }

    public PolicyDefinitionEntity getByVersion(String policyId, String version) {
        return policyRepo.findByPolicyIdAndVersion(policyId, version)
                .orElseThrow(() -> new NotFoundException("Policy '" + policyId + "' version '" + version + "' not found"));
    }

    public Policy getDefinition(String policyId, String version) {
        return deserializePolicy(getByVersion(policyId, version));
    }

    @Transactional
    public PolicyDefinitionEntity updateDraft(String policyId, String version, SavePolicyRequest req) {
        PolicyDefinitionEntity entity = getByVersion(policyId, version);
        if (entity.getStatus() != PolicyStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Only DRAFT policies can be edited in place. Current status: " + entity.getStatus());
        }

        Policy policy = req.getPolicy();
        if (policy == null) throw new IllegalArgumentException("policy is required");

        // Enforce identity — policyId and version are immutable
        policy.setId(policyId);
        policy.setVersion(version);

        ValidationRequest valReq = new ValidationRequest();
        valReq.setPolicy(policy);
        valReq.setUdfs(req.getUdfs());
        ValidationResult result = requestValidator.validate(valReq);
        if (!result.isValid()) {
            throw new IllegalArgumentException(formatValidationErrors(result));
        }

        entity.setName(policy.getName() != null ? policy.getName() : policy.getId());
        entity.setDescription(req.getDescription());
        entity.setBody(toJson(policy));

        return policyRepo.save(entity);
    }

    public List<PolicySummary> listAll() {
        return policyRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(PolicySummary::from).toList();
    }

    /** Paginated list — one entry per unique policyId (latest version), with version count. */
    public Page<PolicySummary> listPaginated(Pageable pageable) {
        Page<PolicyDefinitionEntity> page = policyRepo.findLatestPerPolicy(pageable);

        // Fetch version counts only for the policyIds on this page
        List<String> policyIds = page.getContent().stream()
                .map(PolicyDefinitionEntity::getPolicyId)
                .toList();

        Map<String, Long> versionCounts = policyIds.isEmpty()
                ? Map.of()
                : policyRepo.countVersionsByPolicyIds(policyIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                row -> (String) row[0],
                                row -> ((Number) row[1]).longValue()));

        return page.map(entity -> {
            PolicySummary summary = PolicySummary.from(entity);
            summary.setVersionCount(versionCounts.getOrDefault(entity.getPolicyId(), 1L));
            return summary;
        });
    }

    /** Stats — count of unique policies grouped by their latest version's status. */
    public PolicyStatsDto getStats() {
        PolicyStatsDto dto = new PolicyStatsDto();
        long total = 0;
        for (Object[] row : policyRepo.countByLatestStatus()) {
            PolicyStatus status = (PolicyStatus) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;
            switch (status) {
                case ACTIVE   -> dto.setActive(count);
                case DRAFT    -> dto.setDraft(count);
                case INACTIVE -> dto.setInactive(count);
                case ARCHIVED -> dto.setArchived(count);
            }
        }
        dto.setTotal(total);
        return dto;
    }

    /** Soft-deletes all versions of a policy. Rows remain in DB; @SQLRestriction hides them. */
    @Transactional
    public void deletePolicy(String policyId) {
        List<PolicyDefinitionEntity> versions = policyRepo.findByPolicyId(policyId);
        if (versions.isEmpty()) {
            throw new NotFoundException("Policy '" + policyId + "' not found");
        }
        for (PolicyDefinitionEntity v : versions) {
            v.setDeleted(true);
        }
        policyRepo.saveAll(versions);
    }

    public List<PolicySummary> listVersions(String policyId) {
        return policyRepo.findByPolicyIdOrderByCreatedAtDesc(policyId).stream()
                .map(PolicySummary::from).toList();
    }

    public EvaluationResult evaluateLatestActive(String policyId, EvaluateStoredRequest req) {
        PolicyDefinitionEntity entity = getLatestActive(policyId);
        return evaluate(entity, req);
    }

    public EvaluationResult evaluateByVersion(String policyId, String version, EvaluateStoredRequest req) {
        PolicyDefinitionEntity entity = getByVersion(policyId, version);
        return evaluate(entity, req);
    }

    public EvaluationResult evaluateSubPolicy(String policyId, String version,
                                               EvaluateStoredRequest req, Set<String> activeChain) {
        PolicyDefinitionEntity entity = (version != null && !version.isBlank())
                ? getByVersion(policyId, version) : getLatestActive(policyId);
        String key = entity.getPolicyId() + "@" + entity.getVersion();
        if (activeChain.contains(key)) {
            throw new EvaluationException("Circular policy reference detected: "
                    + String.join(" → ", activeChain) + " → " + key);
        }
        Set<String> newChain = new HashSet<>(activeChain);
        newChain.add(key);
        return doEvaluate(entity, req, newChain);
    }

    public Page<EvaluationLogEntity> listEvaluations(String policyId, Pageable pageable) {
        return evaluationLogRepo.findByPolicyId(policyId, pageable);
    }

    public Page<EvaluationLogEntity> listEvaluationsByVersion(String policyId, String version, Pageable pageable) {
        return evaluationLogRepo.findByPolicyIdAndPolicyVersion(policyId, version, pageable);
    }

    public EvaluationLogEntity getEvaluationLog(String id) {
        return evaluationLogRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Evaluation log '" + id + "' not found"));
    }

    private EvaluationResult evaluate(PolicyDefinitionEntity entity, EvaluateStoredRequest req) {
        String key = entity.getPolicyId() + "@" + entity.getVersion();
        Set<String> chain = new HashSet<>(Set.of(key));
        long start = System.currentTimeMillis();
        try {
            EvaluationResult result = doEvaluate(entity, req, chain);
            persistLog(entity, req, result, System.currentTimeMillis() - start, null);
            return result;
        } catch (Exception ex) {
            persistLog(entity, req, null, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    private EvaluationResult doEvaluate(PolicyDefinitionEntity entity, EvaluateStoredRequest req, Set<String> activeChain) {
        Policy policy = deserializePolicy(entity);

        Map<String, DecisionTable> tables = new HashMap<>();
        for (TableDefinitionEntity t : entity.getTables()) {
            tables.put(t.getTableId(), tableStorageService.deserialize(t));
        }

        Map<String, Lookup> lookups = new HashMap<>();
        for (LookupDefinitionEntity l : entity.getLookups()) {
            lookups.put(l.getLookupId(), lookupStorageService.deserialize(l));
        }

        List<UDF> udfs = new ArrayList<>();
        for (UdfDefinitionEntity u : entity.getUdfs()) {
            udfs.add(udfStorageService.deserialize(u));
        }

        EvaluationRequest evalReq = new EvaluationRequest();
        evalReq.setPolicy(policy);
        evalReq.setTables(tables.isEmpty() ? null : tables);
        evalReq.setLookups(lookups.isEmpty() ? null : lookups);
        evalReq.setUdfs(udfs.isEmpty() ? null : udfs);
        evalReq.setContext(req.getContext());
        evalReq.setTraceLevel(req.getTraceLevel());
        evalReq.setActiveChain(activeChain);

        PolicyType type = policy.getType();
        if (type == null) throw new EvaluationException("Policy type is null");

        return switch (type) {
            case RULE_CHAIN     -> ruleChainEvaluator.evaluate(evalReq);
            case DECISION_TABLE -> decisionTableEvaluator.evaluate(evalReq);
            case SCORECARD      -> scorecardEvaluator.evaluate(evalReq);
        };
    }

    @SneakyThrows
    private void persistLog(PolicyDefinitionEntity entity, EvaluateStoredRequest req,
                            EvaluationResult result, long durationMs, Exception error) {
        EvaluationLogEntity log = new EvaluationLogEntity();
        log.setId(UUID.randomUUID().toString());
        log.setPolicyId(entity.getPolicyId());
        log.setPolicyVersion(entity.getVersion());
        log.setPolicyDefId(entity.getId());
        log.setTraceLevel(req.getTraceLevel());
        log.setEvaluatedBy(req.getEvaluatedBy());
        log.setDurationMs(durationMs);
        log.setContext(toJson(req.getContext()));

        if (error == null) {
            log.setStatus(EvaluationStatus.SUCCESS);
            log.setOutcome(result.getOutcome());
            log.setResult(toJson(result));
        } else {
            log.setStatus(EvaluationStatus.ERROR);
            log.setErrorMessage(error.getMessage());
        }

        evaluationLogRepo.save(log);
    }

    @SneakyThrows
    private Policy deserializePolicy(PolicyDefinitionEntity entity) {
        return objectMapper.readValue(entity.getBody(), Policy.class);
    }

    /**
     * Converts validation errors into a readable bullet-point message.
     * e.g.  "Policy has 2 error(s):
     *          • Edge 'e1' references unknown target node: foo
     *          • Node 'bar' is unreachable from START"
     */
    private String formatValidationErrors(ValidationResult result) {
        List<ValidationResult.ValidationError> errors = result.getErrors();
        if (errors == null || errors.isEmpty()) return "Policy validation failed.";
        StringBuilder sb = new StringBuilder();
        sb.append("Policy has ").append(errors.size())
          .append(errors.size() == 1 ? " error" : " errors").append(":\n");
        for (ValidationResult.ValidationError e : errors) {
            sb.append("  • ").append(e.getMessage());
            if (e.getSuggestion() != null && !e.getSuggestion().isBlank()) {
                sb.append(" — ").append(e.getSuggestion());
            }
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }

    @SneakyThrows
    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }

    private void validateStatusTransition(PolicyStatus current, PolicyStatus next) {
        boolean valid = switch (current) {
            case DRAFT    -> next == PolicyStatus.ACTIVE || next == PolicyStatus.ARCHIVED;
            case ACTIVE   -> next == PolicyStatus.INACTIVE;
            case INACTIVE -> next == PolicyStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Cannot transition policy status from " + current + " to " + next);
        }
    }
}
