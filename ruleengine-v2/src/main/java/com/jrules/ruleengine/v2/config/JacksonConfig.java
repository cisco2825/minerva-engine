package com.jrules.ruleengine.v2.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

@Configuration
public class JacksonConfig {

    @JsonIgnoreProperties({"stackTrace", "suppressed", "cause", "localizedMessage", "message"})
    abstract static class ThrowableMixIn {}

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer problemModuleCustomizer() {
        return builder -> builder
                .mixIn(Throwable.class, ThrowableMixIn.class)
                .modules(
                        new ConstraintViolationProblemModule(),
                        new JavaTimeModule()
                );
    }
}
