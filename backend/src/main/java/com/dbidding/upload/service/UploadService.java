package com.dbidding.upload.service;

import com.dbidding.upload.dto.ImageUploadRequests;
import com.dbidding.upload.dto.ImageUploadResponses;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UploadService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3PresignedUrlProvider presignedUrlProvider;

    public UploadService(S3PresignedUrlProvider presignedUrlProvider) {
        this.presignedUrlProvider = presignedUrlProvider;
    }

    public ImageUploadResponses.PresignedUrlResponse createPresignedUrls(
            ImageUploadRequests.PresignedUrlRequest request) {
        List<ImageUploadResponses.PresignedUpload> uploads = request.files().stream()
                .map(this::presign)
                .toList();
        return new ImageUploadResponses.PresignedUrlResponse(uploads);
    }

    private ImageUploadResponses.PresignedUpload presign(ImageUploadRequests.FileMeta fileMeta) {
        String extension = ALLOWED_CONTENT_TYPES.get(fileMeta.contentType());
        if (extension == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "허용되지 않는 이미지 형식입니다: " + fileMeta.contentType());
        }

        String key = generateKey(extension);
        S3PresignedUrlProvider.PresignedUpload presigned = presignedUrlProvider.presign(key, fileMeta.contentType());
        return new ImageUploadResponses.PresignedUpload(presigned.url(), key, presigned.expiresInSeconds());
    }

    private String generateKey(String extension) {
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        return "uploads/%s/%s.%s".formatted(datePath, UUID.randomUUID(), extension);
    }
}