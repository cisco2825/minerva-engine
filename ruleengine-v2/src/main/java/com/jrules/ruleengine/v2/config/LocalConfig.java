package com.jrules.ruleengine.v2.config;

import com.jrules.ruleengine.s3.S3Service;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.InputStream;

@Configuration
@Profile({"local", "mysql"})
public class LocalConfig {

    @Bean
    public S3Service noOpS3Service() {
        return new S3Service() {
            @Override
            public InputStream getObjectWithBucketName(String bucket, String key) {
                throw new UnsupportedOperationException(
                        "S3 not available in local profile. FILE lookups require a real S3 connection.");
            }

            @Override
            public void putObject(String bucket, String key, InputStream inputStream,
                                  long contentLength, String contentType) {
                throw new UnsupportedOperationException("S3 not available in local profile.");
            }
        };
    }
}
