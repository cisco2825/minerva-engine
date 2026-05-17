package com.jrules.ruleengine.v2.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.InputStream;
import java.net.URI;

@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(
            @Value("${ruleengine.s3.region:}") String region,
            @Value("${ruleengine.s3.endpoint-url:}") String endpointUrl,
            @Value("${ruleengine.s3.access-key:}") String accessKey,
            @Value("${ruleengine.s3.secret-key:}") String secretKey) {

        S3ClientBuilder builder = S3Client.builder();

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            // ── Local / MinIO endpoint ─────────────────────────────────────────
            // MinIO requires path-style access (bucket in URL path, not subdomain)
            builder.endpointOverride(URI.create(endpointUrl))
                   .serviceConfiguration(S3Configuration.builder()
                           .pathStyleAccessEnabled(true)
                           .build())
                   .region(Region.of(region != null && !region.isBlank() ? region : "us-east-1"))
                   .credentialsProvider(
                           (accessKey != null && !accessKey.isBlank())
                               ? StaticCredentialsProvider.create(
                                       AwsBasicCredentials.create(accessKey, secretKey))
                               : DefaultCredentialsProvider.create()
                   );
        } else {
            // ── Real AWS ───────────────────────────────────────────────────────
            builder.region(resolveRegion(region))
                   .credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private Region resolveRegion(String configuredRegion) {
        if (configuredRegion != null && !configuredRegion.isBlank()) {
            return Region.of(configuredRegion);
        }
        AwsRegionProviderChain providerChain = DefaultAwsRegionProviderChain.builder().build();
        Region region = providerChain.getRegion();
        if (region == null) {
            throw new IllegalStateException(
                    "S3 region is not configured. Set ruleengine.s3.region or AWS_REGION.");
        }
        return region;
    }
}
