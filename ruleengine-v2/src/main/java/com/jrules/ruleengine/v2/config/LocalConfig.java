package com.jrules.ruleengine.v2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Placeholder for any local-profile-only beans.
 * S3 no-op fallback has moved to S3ClientConfig to guarantee correct
 * bean evaluation order relative to awsS3Service.
 */
@Configuration
@Profile("local")
public class LocalConfig {
    // S3 no-op bean lives in S3ClientConfig so both S3 beans are
    // in the same @Configuration class — this ensures @ConditionalOnMissingBean
    // evaluates after @Conditional(S3EndpointPresentCondition.class).
}
