package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.storage.dto.SaveLookupRequest;
import com.jrules.ruleengine.v2.storage.entity.AssetStatus;
import com.jrules.ruleengine.v2.storage.entity.LookupDefinitionEntity;
import com.jrules.ruleengine.v2.storage.service.LookupStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/lookups")
@RequiredArgsConstructor
public class LookupStorageController {

    private final LookupStorageService lookupStorageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LookupDefinitionEntity save(@RequestBody SaveLookupRequest request) {
        return lookupStorageService.save(request);
    }

    @GetMapping("/{lookupId}/versions")
    public List<LookupDefinitionEntity> listVersions(@PathVariable String lookupId) {
        return lookupStorageService.getVersions(lookupId);
    }

    @GetMapping("/{lookupId}/versions/{version}")
    public LookupDefinitionEntity getByVersion(@PathVariable String lookupId, @PathVariable String version) {
        return lookupStorageService.getByIdAndVersion(lookupId, version);
    }

    @PatchMapping("/{lookupId}/versions/{version}/status")
    public LookupDefinitionEntity updateStatus(
            @PathVariable String lookupId,
            @PathVariable String version,
            @RequestParam AssetStatus status) {
        return lookupStorageService.updateStatus(lookupId, version, status);
    }
}
