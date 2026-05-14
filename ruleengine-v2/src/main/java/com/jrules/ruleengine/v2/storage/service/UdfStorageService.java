package com.jrules.ruleengine.v2.storage.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.v2.exception.NotFoundException;
import com.jrules.ruleengine.v2.model.udf.UDF;
import com.jrules.ruleengine.v2.model.udf.UDFParam;
import com.jrules.ruleengine.v2.storage.dto.SaveUdfRequest;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.UdfDefinitionEntity;
import com.jrules.ruleengine.v2.storage.repository.UdfDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UdfStorageService {

    private final UdfDefinitionRepository udfRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public UdfDefinitionEntity save(SaveUdfRequest req) {
        UDF udf = req.getUdf();
        String udfId = udf.getName().toUpperCase();
        if (udfRepo.existsByUdfIdAndVersion(udfId, req.getVersion())) {
            throw new IllegalArgumentException(
                    "UDF '" + udfId + "' version '" + req.getVersion() + "' already exists");
        }
        UdfDefinitionEntity entity = new UdfDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUdfId(udfId);
        entity.setVersion(req.getVersion());
        entity.setName(udf.getName());
        entity.setExpression(udf.getExpression());
        entity.setReturnType(udf.getReturnType());
        entity.setParams(toJson(udf.getParams()));
        entity.setStatus(AssetStatus.DRAFT);
        entity.setCreatedBy(req.getCreatedBy());
        return udfRepo.save(entity);
    }

    @Transactional
    public UdfDefinitionEntity findOrCreate(String version, UDF udf, String createdBy) {
        String udfId = udf.getName().toUpperCase();
        return udfRepo.findByUdfIdAndVersion(udfId, version).orElseGet(() -> {
            SaveUdfRequest req = new SaveUdfRequest();
            req.setVersion(version);
            req.setCreatedBy(createdBy);
            req.setUdf(udf);
            return save(req);
        });
    }

    @Transactional
    public UdfDefinitionEntity updateStatus(String udfId, String version, AssetStatus status) {
        UdfDefinitionEntity entity = udfRepo.findByUdfIdAndVersion(udfId, version)
                .orElseThrow(() -> new NotFoundException("UDF '" + udfId + "' version '" + version + "' not found"));
        entity.setStatus(status);
        return udfRepo.save(entity);
    }

    public UdfDefinitionEntity getByIdAndVersion(String udfId, String version) {
        return udfRepo.findByUdfIdAndVersion(udfId, version)
                .orElseThrow(() -> new NotFoundException("UDF '" + udfId + "' version '" + version + "' not found"));
    }

    public List<UdfDefinitionEntity> getVersions(String udfId) {
        return udfRepo.findByUdfIdOrderByCreatedAtDesc(udfId);
    }

    @SneakyThrows
    public UDF deserialize(UdfDefinitionEntity entity) {
        UDF udf = new UDF();
        udf.setName(entity.getName());
        udf.setExpression(entity.getExpression());
        udf.setReturnType(entity.getReturnType());
        udf.setParams(objectMapper.readValue(entity.getParams(), new TypeReference<List<UDFParam>>() {}));
        return udf;
    }

    @SneakyThrows
    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
