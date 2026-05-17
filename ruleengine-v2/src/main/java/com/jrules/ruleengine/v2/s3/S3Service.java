package com.jrules.ruleengine.v2.s3;

import java.io.InputStream;

public interface S3Service {

    InputStream getObjectWithBucketName(String bucket, String key);

    void putObject(String bucket, String key, InputStream inputStream,
                   long contentLength, String contentType);
}
