package com.jrules.ruleengine.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(@Value("${ruleengine.s3.region:}") String region) {
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create());

        Region resolvedRegion = resolveRegion(region);
        builder.region(resolvedRegion);

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
                    "S3 region is not configured. Set ruleengine.s3.region or AWS_REGION/AWS_DEFAULT_REGION."
            );
        }
        return region;
    }
}
