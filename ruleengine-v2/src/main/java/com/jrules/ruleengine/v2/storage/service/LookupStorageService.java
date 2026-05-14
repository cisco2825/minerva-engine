package com.jrules.ruleengine.v2.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.v2.exception.NotFoundException;
import com.jrules.ruleengine.v2.model.lookup.Lookup;
import com.jrules.ruleengine.v2.storage.dto.SaveLookupRequest;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import com.jrules.ruleengine.v2.storage.repository.LookupDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LookupStorageService {

    private final LookupDefinitionRepository lookupRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public LookupDefinitionEntity save(SaveLookupRequest req) {
        if (lookupRepo.existsByLookupIdAndVersion(req.getLookupId(), req.getVersion())) {
            throw new IllegalArgumentException(
                    "Lookup '" + req.getLookupId() + "' version '" + req.getVersion() + "' already exists");
        }
        LookupDefinitionEntity entity = new LookupDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setLookupId(req.getLookupId());
        entity.setVersion(req.getVersion());
        entity.setName(req.getName() != null ? req.getName() : req.getLookupId());
        entity.setType(req.getLookup().getType());
        entity.setStatus(AssetStatus.DRAFT);
        entity.setBody(toJson(req.getLookup()));
        entity.setCreatedBy(req.getCreatedBy());
        return lookupRepo.save(entity);
    }

    @Transactional
    public LookupDefinitionEntity findOrCreate(String lookupId, String version, Lookup lookup, String createdBy) {
        return lookupRepo.findByLookupIdAndVersion(lookupId, version).orElseGet(() -> {
            SaveLookupRequest req = new SaveLookupRequest();
            req.setLookupId(lookupId);
            req.setVersion(version);
            req.setName(lookupId);
            req.setCreatedBy(createdBy);
            req.setLookup(lookup);
            return save(req);
        });
    }

    @Transactional
    public LookupDefinitionEntity updateStatus(String lookupId, String version, AssetStatus status) {
        LookupDefinitionEntity entity = lookupRepo.findByLookupIdAndVersion(lookupId, version)
                .orElseThrow(() -> new NotFoundException("Lookup '" + lookupId + "' version '" + version + "' not found"));
        entity.setStatus(status);
        return lookupRepo.save(entity);
    }

    public LookupDefinitionEntity getByIdAndVersion(String lookupId, String version) {
        return lookupRepo.findByLookupIdAndVersion(lookupId, version)
                .orElseThrow(() -> new NotFoundException("Lookup '" + lookupId + "' version '" + version + "' not found"));
    }

    public List<LookupDefinitionEntity> getVersions(String lookupId) {
        return lookupRepo.findByLookupIdOrderByCreatedAtDesc(lookupId);
    }

    @SneakyThrows
    public Lookup deserialize(LookupDefinitionEntity entity) {
        return objectMapper.readValue(entity.getBody(), Lookup.class);
    }

    @SneakyThrows
    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
