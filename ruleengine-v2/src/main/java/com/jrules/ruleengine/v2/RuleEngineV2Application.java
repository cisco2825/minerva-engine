package com.jrules.ruleengine.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class RuleEngineV2Application {

    public static void main(String[] args) {
        SpringApplication.run(RuleEngineV2Application.class, args);
    }
}
