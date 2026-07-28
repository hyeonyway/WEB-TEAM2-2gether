package com.dbidding.upload.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ImageUploadRequests {
    private ImageUploadRequests() {
    }

    public record PresignedUrlRequest(
            @NotEmpty @Size(max = 10) @Valid List<FileMeta> files
    ) {
    }

    public record FileMeta(
            @NotBlank String fileName,
            @NotBlank String contentType
    ) {
    }
}