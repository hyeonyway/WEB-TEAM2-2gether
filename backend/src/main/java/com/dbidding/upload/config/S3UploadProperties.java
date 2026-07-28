package com.dbidding.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws.s3")
public record S3UploadProperties(
        String bucket,
        int presignDurationSeconds
) {
}