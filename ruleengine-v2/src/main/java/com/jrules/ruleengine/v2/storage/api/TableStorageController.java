package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.auth.security.AuthUtils;
import com.jrules.ruleengine.v2.storage.dto.SaveTableRequest;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.TableDefinitionEntity;
import com.jrules.ruleengine.v2.storage.service.TableStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/tables")
@RequiredArgsConstructor
public class TableStorageController {

    private final TableStorageService tableStorageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableDefinitionEntity save(@RequestBody SaveTableRequest request) {
        request.setCreatedBy(AuthUtils.currentUserName());
        return tableStorageService.save(request);
    }

    @GetMapping("/{tableId}/versions")
    public List<TableDefinitionEntity> listVersions(@PathVariable String tableId) {
        return tableStorageService.getVersions(tableId);
    }

    @GetMapping("/{tableId}/versions/{version}")
    public TableDefinitionEntity getByVersion(@PathVariable String tableId, @PathVariable String version) {
        return tableStorageService.getByIdAndVersion(tableId, version);
    }

    @PatchMapping("/{tableId}/versions/{version}/status")
    public TableDefinitionEntity updateStatus(
            @PathVariable String tableId,
            @PathVariable String version,
            @RequestParam AssetStatus status) {
        return tableStorageService.updateStatus(tableId, version, status);
    }
}
