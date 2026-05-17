package com.jrules.ruleengine.v2.storage.api;

import com.jrules.ruleengine.v2.storage.dto.PolicyStatsDto;
import com.jrules.ruleengine.v2.storage.service.PolicyStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/policy-stats")
@RequiredArgsConstructor
public class PolicyStatsController {

    private final PolicyStorageService policyStorageService;

    @GetMapping
    public PolicyStatsDto getStats() {
        return policyStorageService.getStats();
    }
}
