package com.jrules.ruleengine.v2.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrules.ruleengine.v2.exception.NotFoundException;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.storage.dto.SaveTableRequest;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.TableDefinitionEntity;
import com.jrules.ruleengine.v2.storage.repository.TableDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableStorageService {

    private final TableDefinitionRepository tableRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public TableDefinitionEntity save(SaveTableRequest req) {
        if (tableRepo.existsByTableIdAndVersion(req.getTableId(), req.getVersion())) {
            throw new IllegalArgumentException(
                    "Table '" + req.getTableId() + "' version '" + req.getVersion() + "' already exists");
        }
        TableDefinitionEntity entity = new TableDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTableId(req.getTableId());
        entity.setVersion(req.getVersion());
        entity.setName(req.getTable().getName() != null ? req.getTable().getName() : req.getTableId());
        entity.setHitPolicy(req.getTable().getHitPolicy());
        entity.setStatus(AssetStatus.DRAFT);
        entity.setBody(toJson(req.getTable()));
        entity.setCreatedBy(req.getCreatedBy());
        return tableRepo.save(entity);
    }

    @Transactional
    public TableDefinitionEntity findOrCreate(String tableId, String version, DecisionTable table, String createdBy) {
        return tableRepo.findByTableIdAndVersion(tableId, version).orElseGet(() -> {
            SaveTableRequest req = new SaveTableRequest();
            req.setTableId(tableId);
            req.setVersion(version);
            req.setCreatedBy(createdBy);
            req.setTable(table);
            return save(req);
        });
    }

    @Transactional
    public TableDefinitionEntity updateStatus(String tableId, String version, AssetStatus status) {
        TableDefinitionEntity entity = tableRepo.findByTableIdAndVersion(tableId, version)
                .orElseThrow(() -> new NotFoundException("Table '" + tableId + "' version '" + version + "' not found"));
        entity.setStatus(status);
        return tableRepo.save(entity);
    }

    public TableDefinitionEntity getByIdAndVersion(String tableId, String version) {
        return tableRepo.findByTableIdAndVersion(tableId, version)
                .orElseThrow(() -> new NotFoundException("Table '" + tableId + "' version '" + version + "' not found"));
    }

    public List<TableDefinitionEntity> getVersions(String tableId) {
        return tableRepo.findByTableIdOrderByCreatedAtDesc(tableId);
    }

    @SneakyThrows
    public DecisionTable deserialize(TableDefinitionEntity entity) {
        return objectMapper.readValue(entity.getBody(), DecisionTable.class);
    }

    @SneakyThrows
    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
