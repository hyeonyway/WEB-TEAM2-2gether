package com.dbidding.upload.controller;

import com.dbidding.upload.dto.ImageUploadRequests;
import com.dbidding.upload.dto.ImageUploadResponses;
import com.dbidding.upload.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads/images")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/presigned-url")
    public ImageUploadResponses.PresignedUrlResponse createPresignedUrls(
            @Valid @RequestBody ImageUploadRequests.PresignedUrlRequest request) {
        return uploadService.createPresignedUrls(request);
    }
}