package com.dbidding.upload.service;

import com.dbidding.upload.config.S3UploadProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3PresignedUrlProvider {

    private final S3Presigner s3Presigner;
    private final S3UploadProperties properties;

    public S3PresignedUrlProvider(S3Presigner s3Presigner, S3UploadProperties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public PresignedUpload presign(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        Duration duration = Duration.ofSeconds(properties.presignDurationSeconds());
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presignedRequest.url().toString(), duration.getSeconds());
    }

    public record PresignedUpload(String url, long expiresInSeconds) {
    }
}