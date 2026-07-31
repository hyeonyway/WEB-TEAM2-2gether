package com.dbidding.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.dbidding.upload.dto.ImageUploadRequests;
import com.dbidding.upload.dto.ImageUploadResponses;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private S3PresignedUrlProvider presignedUrlProvider;

    private UploadService uploadService;

    @Test
    void 이미지_파일들에_대해_presignedURL을_발급한다() {
        uploadService = new UploadService(presignedUrlProvider);
        given(presignedUrlProvider.presign(anyString(), anyString()))
                .willReturn(new S3PresignedUrlProvider.PresignedUpload("https://example.com/signed", 300));

        ImageUploadRequests.PresignedUrlRequest request = new ImageUploadRequests.PresignedUrlRequest(
                List.of(
                        new ImageUploadRequests.FileMeta("card1.jpg", "image/jpeg"),
                        new ImageUploadRequests.FileMeta("card2.png", "image/png")
                )
        );

        ImageUploadResponses.PresignedUrlResponse response = uploadService.createPresignedUrls(request);

        assertThat(response.uploads()).hasSize(2);
        response.uploads().forEach(upload -> {
            assertThat(upload.uploadUrl()).isEqualTo("https://example.com/signed");
            assertThat(upload.expiresInSeconds()).isEqualTo(300);
            assertThat(upload.uploadToken()).startsWith("upload/auctionImage/");
        });
        assertThat(response.uploads().get(0).uploadToken()).endsWith(".jpg");
        assertThat(response.uploads().get(1).uploadToken()).endsWith(".png");
    }

    @Test
    void 허용되지_않는_contentType이면_400_예외를_던진다() {
        uploadService = new UploadService(presignedUrlProvider);
        ImageUploadRequests.PresignedUrlRequest request = new ImageUploadRequests.PresignedUrlRequest(
                List.of(new ImageUploadRequests.FileMeta("virus.exe", "application/octet-stream"))
        );

        assertThatThrownBy(() -> uploadService.createPresignedUrls(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("허용되지 않는 이미지 형식");
    }
}