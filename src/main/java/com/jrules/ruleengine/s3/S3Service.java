package com.jrules.ruleengine.s3;

import java.io.InputStream;

public interface S3Service {

    InputStream getObjectWithBucketName(String bucket, String key);

    void putObject(String bucket, String key, InputStream inputStream, long contentLength, String contentType);

    default void putObject(String bucket, String key, byte[] bytes, String contentType) {
        putObject(bucket, key, new java.io.ByteArrayInputStream(bytes), bytes.length, contentType);
    }
}
