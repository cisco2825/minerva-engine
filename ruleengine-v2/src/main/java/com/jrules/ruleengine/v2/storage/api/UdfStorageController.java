package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.storage.dto.SaveUdfRequest;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.UdfDefinitionEntity;
import com.jrules.ruleengine.v2.storage.service.UdfStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/udfs")
@RequiredArgsConstructor
public class UdfStorageController {

    private final UdfStorageService udfStorageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UdfDefinitionEntity save(@RequestBody SaveUdfRequest request) {
        return udfStorageService.save(request);
    }

    @GetMapping("/{udfId}/versions")
    public List<UdfDefinitionEntity> listVersions(@PathVariable String udfId) {
        return udfStorageService.getVersions(udfId);
    }

    @GetMapping("/{udfId}/versions/{version}")
    public UdfDefinitionEntity getByVersion(@PathVariable String udfId, @PathVariable String version) {
        return udfStorageService.getByIdAndVersion(udfId, version);
    }

    @PatchMapping("/{udfId}/versions/{version}/status")
    public UdfDefinitionEntity updateStatus(
            @PathVariable String udfId,
            @PathVariable String version,
            @RequestParam AssetStatus status) {
        return udfStorageService.updateStatus(udfId, version, status);
    }
}
