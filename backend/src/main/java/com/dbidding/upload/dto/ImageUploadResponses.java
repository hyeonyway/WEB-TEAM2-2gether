package com.dbidding.upload.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class ImageUploadResponses {
    private ImageUploadResponses() {
    }

    public record PresignedUrlResponse(
            List<PresignedUpload> uploads
    ) {
    }

    public record PresignedUpload(
            @JsonProperty("upload_url") String uploadUrl,
            @JsonProperty("upload_token") String uploadToken,
            @JsonProperty("expires_in_seconds") long expiresInSeconds
    ) {
    }
}